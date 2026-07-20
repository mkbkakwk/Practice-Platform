import { mkdir } from "fs/promises";
import { config } from "./config.js";
import { createApp } from "./app.js";
import { prisma } from "./prisma.js";
import { seedIfEmpty } from "./seed.js";

async function main() {
  // Ensure the judge workspace exists.
  await mkdir(config.judgeWorkspace, { recursive: true });

  // NOTE: schema is applied via `prisma db push` in the container entrypoint
  // (see Dockerfile CMD). Here we only connect and seed.
  await prisma.$connect();
  await seedIfEmpty();

  const app = createApp();
  app.listen(config.port, () => {
    console.log(`[OJ] backend listening on :${config.port}`);
  });
}

main().catch((err) => {
  console.error("[fatal]", err);
  process.exit(1);
});
