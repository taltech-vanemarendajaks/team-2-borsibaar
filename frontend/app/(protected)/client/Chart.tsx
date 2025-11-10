"use client";

import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import * as d3 from "d3";
import { InvDto } from "./page";

// ---------- Tallinn time helpers ----------
const tallinnParts = (d: Date) => {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/Tallinn",
    hour12: false,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).formatToParts(d);
  const m: Record<string, number> = Object.create(null);
  for (const p of parts) if (p.type !== "literal") m[p.type] = Number(p.value);
  return m; // {year, month, day, hour, minute, second}
};

const snapTallinn = (d: Date, stepMin: number) => {
  const { minute, second } = tallinnParts(d);
  const snapDelta = ((minute % stepMin) * 60 + second) * 1000 + d.getMilliseconds();
  return new Date(d.getTime() - snapDelta);
};

const tallinnTicks = (cutoff: Date, now: Date, stepMin: number) => {
  const first = snapTallinn(now, stepMin);
  const stepMs = stepMin * 60 * 1000;
  const out: Date[] = [];
  for (let t = first.getTime(); t >= cutoff.getTime(); t -= stepMs) out.push(new Date(t));
  out.reverse();
  if (+out[0] !== +cutoff) out.unshift(cutoff);
  if (+out[out.length - 1] !== +now) out.push(now);
  return out;
};

// ---------- Types ----------
type HistoryDto = {
  id: number;
  inventoryId: number;
  priceBefore: number;
  priceAfter: number;
  createdAt: string;
};
type CurrentHistory = {
  productInv: InvDto;
  priceHistory: HistoryDto[];
};

export default function PriceHistoryGraphFancy({ groups }: { groups: Record<string, InvDto[]> }) {
  const [current, setCurrent] = useState<CurrentHistory | null>(null);
  const [error, setError] = useState<string | null>(null);

  // rotation (stable across reloads)
  const groupsRef = useRef<Record<string, InvDto[]>>({});
  useEffect(() => { groupsRef.current = groups; }, [groups]);
  const activeIdxRef = useRef(0);
  const activeProductRef = useRef<InvDto | null>(null);

  const flatten = useCallback((g: Record<string, InvDto[]>) => {
    const catNames = Object.keys(g).sort((a, b) => a.localeCompare(b));
    return catNames.flatMap((name) =>
      [...(g[name] ?? [])].sort((a, b) => a.productId - b.productId)
    );
  }, []);

  const loadPriceHistory = useCallback(async (productInv: InvDto) => {
    if (!productInv) return;
    try {
      setError(null);
      const res = await fetch(`/api/backend/inventory/product/${productInv.productId}/history`, {
        cache: "no-store",
        credentials: "include",
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const historyJson: HistoryDto[] = await res.json();
      setCurrent({ productInv, priceHistory: historyJson });
    } catch (e: any) {
      setError(e?.message || "Failed to fetch history");
    }
  }, []);

  const rotateOnce = useCallback(() => {
    const flat = flatten(groupsRef.current);
    if (flat.length === 0) return;
    const cur = activeProductRef.current;
    let nextIdx: number;
    if (cur) {
      const i = flat.findIndex((p) => p.productId === cur.productId);
      nextIdx = i >= 0 ? (i + 1) % flat.length : activeIdxRef.current % flat.length;
    } else nextIdx = activeIdxRef.current % flat.length;
    const next = flat[nextIdx];
    activeIdxRef.current = nextIdx;
    activeProductRef.current = next;
    loadPriceHistory(next);
  }, [flatten, loadPriceHistory]);

  useEffect(() => {
    if (!activeProductRef.current) {
      const flat = flatten(groupsRef.current);
      if (flat.length > 0) {
        activeIdxRef.current %= flat.length;
        const initial = flat[activeIdxRef.current];
        activeProductRef.current = initial;
        loadPriceHistory(initial);
      }
    }
    const id = setInterval(rotateOnce, 5000);
    return () => clearInterval(id);
  }, [flatten, rotateOnce, loadPriceHistory]);

  // build full step series (no zero baseline)
  const series = useMemo(() => {
    if (!current) return [] as { date: Date; price: number }[];

    const product = current.productInv;
    const hist = [...(current.priceHistory ?? [])]
      .map(h => ({ ts: new Date(h.createdAt), before: Number(h.priceBefore), after: Number(h.priceAfter) }))
      .filter(h => !isNaN(h.ts.getTime()))
      .sort((a, b) => a.ts.getTime() - b.ts.getTime());

    const firstBefore = hist.find(h => Number.isFinite(h.before) && h.before !== 0)?.before;
    const base =
      (firstBefore ??
        (product as any)?.basePrice ??
        product.unitPrice ??
        hist[0]?.after ??
        0) as number;

    const out: { date: Date; price: number }[] = [];
    if (hist.length === 0) {
      const now = new Date();
      out.push({ date: new Date(now.getTime() - 1), price: base }, { date: now, price: base });
      return out;
    }
    out.push({ date: new Date(hist[0].ts.getTime() - 1), price: base });
    let last = base;
    for (const e of hist) {
      const next = Number.isFinite(e.after) ? e.after : last;
      out.push({ date: e.ts, price: next });
      last = next;
    }
    if (+out[out.length - 1].date < Date.now()) out.push({ date: new Date(), price: last });
    return out;
  }, [current]);

  // ---- WINDOW: last 1 hour + delta ----
  const HOURS_WINDOW = 1;
  const { data: windowed, cutoff, now, delta } = useMemo(() => {
    const _now = new Date();
    const _cutoff = new Date(_now.getTime() - HOURS_WINDOW * 3600_000);
    if (!series.length) return { data: series, cutoff: _cutoff, now: _now, delta: 0 };

    // last point at/before cutoff
    let i = series.length - 1;
    while (i >= 0 && series[i].date > _cutoff) i--;

    const out: { date: Date; price: number }[] = [];
    const seedPrice = i >= 0 ? series[i].price : series[0].price;
    out.push({ date: _cutoff, price: seedPrice });

    for (let j = Math.max(i + 1, 0); j < series.length; j++) {
      if (series[j].date >= _cutoff) out.push(series[j]);
    }
    const last = out[out.length - 1];
    if (+last.date < +_now) out.push({ date: _now, price: last.price });

    const _delta = out[out.length - 1].price - out[0].price; // change over last hour
    return { data: out, cutoff: _cutoff, now: _now, delta: _delta };
  }, [series]);

  // ---------- D3 drawing ----------
  const wrapRef = useRef<HTMLDivElement | null>(null);
  const money = useMemo(() => new Intl.NumberFormat("et-EE", { style: "currency", currency: "EUR" }), []);

  useEffect(() => {
    if (!wrapRef.current) return;
    d3.select(wrapRef.current).selectAll("*").remove();

    const w = wrapRef.current.clientWidth || 760;
    const h = Math.max(320, Math.round(w * 0.48));
    const margin = { top: 56, right: 28, bottom: 56, left: 72 };

    const svg = d3.select(wrapRef.current).append("svg").attr("width", w).attr("height", h);
    svg.style("display", "block");

    // background card
    const defs = svg.append("defs");
    const r = 16;
    defs.append("clipPath").attr("id", "clipR")
      .append("rect").attr("width", w).attr("height", h).attr("rx", r).attr("ry", r);

    const bgGrad = defs.append("linearGradient").attr("id", "bgGrad").attr("x1", "0").attr("x2", "1").attr("y1", "0").attr("y2", "1");
    bgGrad.append("stop").attr("offset", "0%").attr("stop-color", "#1a1630");
    bgGrad.append("stop").attr("offset", "100%").attr("stop-color", "#16132a");

    svg.append("rect")
      .attr("width", w).attr("height", h)
      .attr("rx", r).attr("ry", r)
      .attr("fill", "url(#bgGrad)")
      .attr("stroke", "rgba(255,255,255,0.06)")
      .attr("clip-path", "url(#clipR)");

    // title
    svg.append("text")
      .attr("x", w / 2)
      .attr("y", 28)
      .attr("text-anchor", "middle")
      .attr("fill", "#e5e7eb")
      .style("font-weight", 800)
      .style("font-size", "16px")
      .text(`${current?.productInv.productName ?? "—"} • Last 1h`);

    // delta pill (top-right)
    const deltaStr = `${delta >= 0 ? "+" : "−"}${money.format(Math.abs(delta)).replace("€", "").trim()} €`;
    const badge = svg.append("g").attr("transform", `translate(${w - 16}, 18)`);
    const badgeText = badge.append("text")
      .attr("text-anchor", "end")
      .attr("fill", delta >= 0 ? "#86efac" : "#fca5a5")
      .style("font-weight", 800)
      .style("font-size", "12px")
      .text(deltaStr);
    const bb = (badgeText.node() as SVGTextElement).getBBox();
    badge.insert("rect", "text")
      .attr("x", bb.x - 10)
      .attr("y", bb.y - 6)
      .attr("width", bb.width + 20)
      .attr("height", bb.height + 12)
      .attr("rx", 10).attr("ry", 10)
      .attr("fill", "rgba(0,0,0,0.35)")
      .attr("stroke", "rgba(255,255,255,0.08)");

    const g = svg.append("g").attr("transform", `translate(${margin.left},${margin.top})`);
    const innerW = Math.max(140, w - margin.left - margin.right);
    const innerH = Math.max(120, h - margin.top - margin.bottom);

    if (!windowed.length) {
      g.append("text").attr("fill", "#9aa4b2").attr("y", innerH / 2).text("No data");
      return;
    }

    // scales for the 1h window
    const x = d3.scaleTime().domain([cutoff, now]).range([0, innerW]);
    const pMin = d3.min(windowed, d => d.price) ?? 0;
    const pMax = d3.max(windowed, d => d.price) ?? 1;
    const pad = Math.max((pMax - pMin) * 0.06, 0.1);
    const y = d3.scaleLinear().domain([pMin - pad, pMax + pad]).nice().range([innerH, 0]);

    // ---------- X axis: Tallinn-aligned ticks ----------
    const stepMin = innerW < 520 ? 15 : 10; // avoid crowding on smaller widths
    let ticks = tallinnTicks(cutoff, now, stepMin);

    const maxTicks = Math.max(3, Math.floor(innerW / 110)); // de-clutter
    if (ticks.length > maxTicks) {
      const keep = new Set<number>([+ticks[0], +ticks[ticks.length - 1]]);
      const mids = ticks.slice(1, -1);
      const step = Math.ceil(mids.length / Math.max(1, maxTicks - 2));
      for (let i = 0; i < mids.length; i += step) keep.add(+mids[i]);
      ticks = ticks.filter(t => keep.has(+t));
    }

    const tallinnFmt = (d: Date) =>
      new Intl.DateTimeFormat("en-US", { timeZone: "Europe/Tallinn", hour: "2-digit", minute: "2-digit" }).format(d);

    g.append("g")
      .attr("transform", `translate(0,${innerH})`)
      .call(d3.axisBottom<Date>(x).tickValues(ticks).tickFormat(d => tallinnFmt(d) as any).tickSizeOuter(0))
      .call(s => s.select(".domain").attr("stroke", "rgba(255,255,255,0.08)"))
      .call(s => s.selectAll("text").attr("fill", "#cdd6f4").style("font-size", "12px"))
      .call(s => s.selectAll("line").attr("stroke", "rgba(255,255,255,0.08)"));

    // Y axis
    g.append("g")
      .call(d3.axisLeft<number>(y).ticks(6).tickFormat(v => money.format(Number(v)) as any))
      .call(s => s.select(".domain").attr("stroke", "rgba(255,255,255,0.08)"))
      .call(s => s.selectAll("text").attr("fill", "#cdd6f4").style("font-size", "12px"))
      .call(s => s.selectAll("line").attr("stroke", "rgba(255,255,255,0.08)"));

    g.append("text")
      .attr("transform", `rotate(-90)`)
      .attr("x", -innerH / 2)
      .attr("y", -56)
      .attr("text-anchor", "middle")
      .style("font-size", "12px")
      .style("font-weight", 700)
      .attr("fill", "#d6d9e6")
      .text("Price (EUR)");

    // grid
    g.append("g")
      .call(d3.axisLeft(y).ticks(6).tickSize(-innerW).tickFormat(() => ""))
      .call(s => s.select(".domain").remove())
      .call(s => s.selectAll(".tick line").attr("stroke", "#2a2f45").attr("stroke-opacity", 0.35));

    // line glow + area
    const glow = defs.append("filter").attr("id", "glowBlue");
    glow.append("feGaussianBlur").attr("stdDeviation", 1.6).attr("result", "b");
    const merge = glow.append("feMerge");
    merge.append("feMergeNode").attr("in", "b");
    merge.append("feMergeNode").attr("in", "SourceGraphic");

    const areaGrad = defs.append("linearGradient").attr("id", "areaBlue").attr("x1", "0").attr("x2", "0").attr("y1", "0").attr("y2", "1");
    areaGrad.append("stop").attr("offset", "0%").attr("stop-color", "#8aa6ff").attr("stop-opacity", 0.22);
    areaGrad.append("stop").attr("offset", "100%").attr("stop-color", "#8aa6ff").attr("stop-opacity", 0.02);

    const line = d3.line<{ date: Date; price: number }>()
      .x(d => x(d.date))
      .y(d => y(d.price))
      .curve(d3.curveStepAfter);

    const area = d3.area<{ date: Date; price: number }>()
      .x(d => x(d.date))
      .y0(() => y(pMin - pad))
      .y1(d => y(d.price))
      .curve(d3.curveStepAfter);

    g.append("path").datum(windowed).attr("d", area as any).attr("fill", "url(#areaBlue)");
    g.append("path")
      .datum(windowed)
      .attr("d", line as any)
      .attr("fill", "none")
      .attr("stroke", "#8aa6ff")
      .attr("stroke-width", 2.6)
      .attr("filter", "url(#glowBlue)");

    // hover
    const overlay = g.append("rect").attr("width", innerW).attr("height", innerH).attr("fill", "transparent").style("cursor", "crosshair");
    const cursor = g.append("line").attr("y1", 0).attr("y2", innerH).attr("stroke", "#a6baff").attr("stroke-opacity", 0.25).style("display", "none");
    const dot = g.append("circle").attr("r", 4).attr("fill", "#8aa6ff").attr("stroke", "#141325").attr("stroke-width", 1.6).style("display", "none");

    const tip = g.append("g").style("display", "none");
    const tipBox = tip.append("rect").attr("rx", 8).attr("ry", 8).attr("fill", "rgba(13,18,31,0.96)").attr("stroke", "rgba(148,163,184,0.35)");
    const tipText = tip.append("text").attr("x", 10).attr("y", 12).attr("fill", "#e5e7eb").style("font-size", "12px").style("font-weight", 700);

    const bisect = d3.bisector<{ date: Date }, Date>(d => d.date).left;

    function show(mx: number) {
      const t = x.invert(mx);
      const i = Math.max(0, Math.min(windowed.length - 1, bisect(windowed, t)));
      const a = windowed[i - 1] ?? windowed[i];
      const b = windowed[i] ?? windowed[i - 1];
      const d = !a ? b : !b ? a : +t - +a.date > +b.date - +t ? b : a;

      cursor.style("display", null).attr("x1", x(d.date)).attr("x2", x(d.date));
      dot.style("display", null).attr("cx", x(d.date)).attr("cy", y(d.price));
      tip.style("display", null);

      const label = `${new Intl.DateTimeFormat("en-US", { timeZone: "Europe/Tallinn", hour: "2-digit", minute: "2-digit" }).format(d.date)} • ${money.format(d.price)}`;
      tipText.text(label);
      const bb2 = (tipText.node() as SVGTextElement).getBBox();
      tipBox.attr("x", bb2.x - 8).attr("y", bb2.y - 6).attr("width", bb2.width + 16).attr("height", bb2.height + 12);

      const tx = Math.min(innerW - (bb2.width + 22), Math.max(0, x(d.date) + 10));
      const ty = Math.max(0, y(d.price) - (bb2.height + 18));
      tip.attr("transform", `translate(${tx},${ty})`);
    }

    overlay
      .on("mousemove", (ev) => { const [mx] = d3.pointer(ev); show(mx); })
      .on("mouseenter", () => { cursor.style("display", null); tip.style("display", null); dot.style("display", null); })
      .on("mouseleave", () => { cursor.style("display", "none"); tip.style("display", "none"); dot.style("display", "none"); });

  }, [windowed, cutoff, now, delta, current?.productInv?.productName, money]);

  return (
    <div className="rounded-2xl border border-white/10 bg-transparent p-0 shadow-[0_0_0_1px_rgba(255,255,255,0.03)_inset]">
      {error && <div className="px-4 py-3 text-sm text-rose-300">{error}</div>}
      <div ref={wrapRef} className="w-full" />
    </div>
  );
}
