import { drizzle, type NodePgDatabase } from "drizzle-orm/node-postgres"
import { Pool } from "pg"

import * as schema from "./schema.js"

export type Database = NodePgDatabase<typeof schema> & { $client: Pool }

export function createDatabase(connectionString: string): Database {
  const client = new Pool({ connectionString })
  return drizzle({ client, schema })
}

export function createDatabaseFromEnv(): Database {
  const connectionString = process.env.DATABASE_URL
  if (!connectionString) throw new Error("DATABASE_URL is required")
  return createDatabase(connectionString)
}

export async function closeDatabase(database: Database): Promise<void> {
  await database.$client.end()
}
