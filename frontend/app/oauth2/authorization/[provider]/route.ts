import { NextRequest, NextResponse } from "next/server";
import { backendUrl } from "@/utils/constants";

export const dynamic = "force-dynamic";

export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ provider: string }> }
) {
  const { provider } = await params;
  const target = `${backendUrl}/oauth2/authorization/${encodeURIComponent(provider)}`;
  return NextResponse.redirect(target, { status: 307 });
}
