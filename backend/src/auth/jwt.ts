import jwt from "jsonwebtoken";
import { config } from "../config.js";

export interface TokenPayload {
  id: number;
  username: string;
  role: "USER" | "ADMIN";
}

export function signToken(payload: TokenPayload): string {
  return jwt.sign(payload, config.jwtSecret, { expiresIn: config.jwtExpiresIn } as jwt.SignOptions);
}

export function verifyToken(token: string): TokenPayload {
  return jwt.verify(token, config.jwtSecret) as TokenPayload;
}
