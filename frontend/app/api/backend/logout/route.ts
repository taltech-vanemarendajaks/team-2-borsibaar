import { NextRequest } from "next/server";
import { handleLogout } from "@/lib/server/logout";

export async function POST(request: NextRequest) {
  return handleLogout(request);
}
