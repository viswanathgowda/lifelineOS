import { openDB, type DBSchema, type IDBPDatabase } from "idb";

type BlobRecord = {
  id: string;
  sizeBytes: number;
  contentType: string;
  blob: Blob;
  updatedAt: number;
};

interface VaultDb extends DBSchema {
  blobs: {
    key: string;
    value: BlobRecord;
  };
}

let dbPromise: Promise<IDBPDatabase<VaultDb>> | null = null;

function getDb() {
  if (typeof window === "undefined") {
    throw new Error("IndexedDB is only available in the browser");
  }
  if (!dbPromise) {
    dbPromise = openDB<VaultDb>("lifeline-vault-cache", 1, {
      upgrade(db) {
        db.createObjectStore("blobs", { keyPath: "id" });
      },
    });
  }
  return dbPromise;
}

export async function getCachedBlob(id: string, sizeBytes?: number | null) {
  const db = await getDb();
  const record = await db.get("blobs", id);
  if (!record) return null;
  if (sizeBytes != null && record.sizeBytes !== sizeBytes) return null;
  return record.blob;
}

export async function putCachedBlob(
  id: string,
  blob: Blob,
  sizeBytes?: number | null,
) {
  const db = await getDb();
  await db.put("blobs", {
    id,
    sizeBytes: sizeBytes ?? blob.size,
    contentType: blob.type || "application/octet-stream",
    blob,
    updatedAt: Date.now(),
  });
}

export async function clearCachedBlobs() {
  const db = await getDb();
  await db.clear("blobs");
}
