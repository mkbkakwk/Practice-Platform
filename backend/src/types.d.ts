// Augment Express Request with the authenticated user payload.
// Express's Request interface is declared in `express-serve-static-core` and
// re-exported by `express`, so we augment the core module. Keeping this file
// a *module* (via the side-effect imports) preserves Express's overload
// inference for route handlers while still adding the `user` field.
import "express";
import "express-serve-static-core";

declare module "express-serve-static-core" {
  interface Request {
    user?: {
      id: number;
      username: string;
      role: "USER" | "ADMIN";
    };
  }
}
