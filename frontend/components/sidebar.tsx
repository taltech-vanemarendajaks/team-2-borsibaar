"use client";

import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar";

import { ChartLine, Home, LogOut, Package, ShoppingCart } from "lucide-react";

// Menu items.
const items = [
  {
    title: "Dashboard",
    url: "" + "/dashboard",
    icon: Home,
  },
  {
    title: "POS",
    url: "/pos",
    icon: ShoppingCart,
  },
  {
    title: "Inventory",
    url: "/inventory",
    icon: Package,
  },
  {
    title: "Client",
    url: "/client",
    icon: ChartLine,
  },
];

function SidebarContentItems() {
  const { state } = useSidebar();
  const isCollapsed = state === "collapsed";

  return (
    <>
      {items.map((item) => (
        <SidebarMenuItem key={item.title}>
          <SidebarMenuButton asChild size="lg" tooltip={item.title}>
            <a href={item.url} className="flex items-center">
              <item.icon className="!w-6 !h-6" />
              {!isCollapsed && (
                <span className="text-lg font-medium">{item.title}</span>
              )}
            </a>
          </SidebarMenuButton>
        </SidebarMenuItem>
      ))}
    </>
  );
}

function SidebarFooterContent() {
  const { state } = useSidebar();
  const isCollapsed = state === "collapsed";

  const handleLogout = async () => {
    try {
      await fetch("/api/logout", { method: "POST" });
      window.location.href = "/login";
      console.log("Logged out successfully");
    } catch (error) {
      console.error("Logout failed:", error);
    }
  };

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <SidebarMenuButton size="lg" onClick={handleLogout} tooltip="Logout">
          <LogOut className="!w-6 !h-6" />
          {!isCollapsed && <span className="text-lg font-medium">Logout</span>}
        </SidebarMenuButton>
      </SidebarMenuItem>
    </SidebarMenu>
  );
}

export function AppSidebar() {
  return (
    <Sidebar variant="floating" collapsible="icon" className="p-4 pr-0">
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel className="text-md">Börsibaar</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu className="gap-3">
              <SidebarContentItems />
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
      <SidebarFooter>
        <SidebarFooterContent />
      </SidebarFooter>
    </Sidebar>
  );
}
