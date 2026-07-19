/**
 * legal-entity kotoba — legalEntity + filing + ownership registries + coverage.
 * AT PDS records (no RW). Filings FK→entity; ownership edges FK both owner and
 * owned entities. Public corporate-registry data only.
 */

import type { Etzhayyim } from "@etzhayyim/sdk";
import {
  ENTITY_COLLECTION,
  ENTITY_STATUSES,
  FILING_COLLECTION,
  OWNERSHIP_COLLECTION,
  entityDidFor,
  entityRkey,
  filingDidFor,
  filingRkey,
  isJurisdiction,
  isLei,
  isPermille,
  ownershipDidFor,
  ownershipRkey,
  type AddFilingInput,
  type AddFilingOutput,
  type CoverageInput,
  type CoverageOutput,
  type FilingRecord,
  type FilingView,
  type GetEntityInput,
  type GetEntityOutput,
  type LegalEntityRecord,
  type LegalEntityView,
  type ListEntitiesInput,
  type ListEntitiesOutput,
  type ListFilingsInput,
  type ListFilingsOutput,
  type ListOwnershipInput,
  type ListOwnershipOutput,
  type OwnershipRecord,
  type OwnershipView,
  type RecordOwnershipInput,
  type RecordOwnershipOutput,
  type RegisterEntityInput,
  type RegisterEntityOutput,
  type SetEntityStatusInput,
  type SetEntityStatusOutput,
} from "./types.js";

const PAGE_LIMIT = 100;
const DEFAULT_MAX_SCAN = 10_000;

async function exists(e: Etzhayyim, collection: string, rkey: string): Promise<boolean> {
  const resp = await e.read({ collection, rkey }).catch(() => ({ records: [] }));
  return Boolean(resp.records[0]?.value);
}

async function scanAll<T>(e: Etzhayyim, collection: string, maxScan: number, onRow: (v: T) => void): Promise<number> {
  let cursor: string | undefined;
  let scanned = 0;
  while (scanned < maxScan) {
    const page = await e.read<T>({ collection, cursor, limit: PAGE_LIMIT });
    for (const r of page.records) {
      if (scanned >= maxScan) break;
      onRow(r.value);
      scanned += 1;
    }
    if (scanned >= maxScan || !page.cursor || page.records.length < PAGE_LIMIT) break;
    cursor = page.cursor;
  }
  return scanned;
}

// ─── Legal entity ───────────────────────────────────────────────────

export async function registerEntity(e: Etzhayyim, input: RegisterEntityInput): Promise<RegisterEntityOutput> {
  if (!input.entityId || !input.legalName || !input.sourceRegistry) return { status: "rejected", error: "missingRequiredFields" };
  const jurisdiction = input.jurisdiction?.toUpperCase();
  if (!isJurisdiction(jurisdiction ?? "")) return { status: "rejected", error: "invalidJurisdiction" };
  if (input.lei && !isLei(input.lei.toUpperCase())) return { status: "rejected", error: "invalidLei" };
  const rkey = entityRkey(input.entityId);
  const existing = await e.read<LegalEntityRecord>({ collection: ENTITY_COLLECTION, rkey }).catch(() => ({ records: [] }));
  if (existing.records[0]?.value) {
    return { status: "alreadyExists", entityUri: existing.records[0].uri, did: existing.records[0].value.did, entityId: input.entityId };
  }
  const did = entityDidFor(input.entityId);
  const record: LegalEntityRecord = {
    did,
    entityId: input.entityId,
    legalName: input.legalName,
    lei: input.lei?.toUpperCase(),
    jurisdiction: jurisdiction!,
    registrationNumber: input.registrationNumber,
    status: "active",
    incorporationDate: input.incorporationDate,
    sourceRegistry: input.sourceRegistry,
    createdAt: new Date().toISOString(),
  };
  const receipt = await e.write({ collection: ENTITY_COLLECTION, record: record as unknown as Record<string, unknown>, rkey });
  return { status: "registered", entityUri: receipt.uri, did, entityId: input.entityId };
}

export async function setEntityStatus(e: Etzhayyim, input: SetEntityStatusInput): Promise<SetEntityStatusOutput> {
  if (!input.entityId || !ENTITY_STATUSES.has(input.status)) return { status: "rejected", error: "invalidStatus" };
  const rkey = entityRkey(input.entityId);
  const resp = await e.read<LegalEntityRecord>({ collection: ENTITY_COLLECTION, rkey }).catch(() => ({ records: [] }));
  const entity = resp.records[0]?.value;
  if (!entity) return { status: "notFound", error: "entityNotFound" };
  if (entity.status === "dissolved") return { status: "rejected", error: "entityDissolved" };
  await e.write({ collection: ENTITY_COLLECTION, record: { ...entity, status: input.status } as unknown as Record<string, unknown>, rkey });
  return { status: "updated", entityId: input.entityId, newStatus: input.status };
}

export async function getEntity(e: Etzhayyim, input: GetEntityInput): Promise<GetEntityOutput> {
  if (!input.entityId) return { error: "invalidEntityId" };
  const resp = await e.read<LegalEntityRecord>({ collection: ENTITY_COLLECTION, rkey: entityRkey(input.entityId) }).catch(() => ({ records: [] }));
  const r = resp.records[0];
  if (!r) return { error: "notFound" };
  return { entity: { ...r.value, entityUri: r.uri } };
}

export async function listEntities(e: Etzhayyim, input: ListEntitiesInput = {}): Promise<ListEntitiesOutput> {
  const limit = Math.min(input.limit ?? 50, 200);
  const resp = await e.read<LegalEntityRecord>({ collection: ENTITY_COLLECTION, cursor: input.cursor, limit });
  const q = input.q?.toLowerCase();
  const jurisdiction = input.jurisdiction?.toUpperCase();
  const items: LegalEntityView[] = resp.records
    .filter((r) => {
      const v = r.value;
      if (jurisdiction && v.jurisdiction !== jurisdiction) return false;
      if (input.status && v.status !== input.status) return false;
      if (q && !v.legalName.toLowerCase().includes(q)) return false;
      return true;
    })
    .map((r) => ({ ...r.value, entityUri: r.uri }));
  return { items, cursor: resp.cursor, total: items.length };
}

// ─── Filing ─────────────────────────────────────────────────────────

export async function addFiling(e: Etzhayyim, input: AddFilingInput): Promise<AddFilingOutput> {
  if (!input.filingId || !input.entityId || !input.filingType || !input.filedAt || !input.sourceUrl) {
    return { status: "rejected", error: "missingRequiredFields" };
  }
  if (!(await exists(e, ENTITY_COLLECTION, entityRkey(input.entityId)))) {
    return { status: "entityNotFound", error: `entityNotFound:${input.entityId}` };
  }
  const rkey = filingRkey(input.filingId);
  const existing = await e.read<FilingRecord>({ collection: FILING_COLLECTION, rkey }).catch(() => ({ records: [] }));
  if (existing.records[0]?.value) {
    return { status: "alreadyExists", filingUri: existing.records[0].uri, did: existing.records[0].value.did, filingId: input.filingId };
  }
  const did = filingDidFor(input.filingId);
  const record: FilingRecord = {
    did,
    filingId: input.filingId,
    entityId: input.entityId,
    filingType: input.filingType,
    filedAt: input.filedAt,
    sourceUrl: input.sourceUrl,
    createdAt: new Date().toISOString(),
  };
  const receipt = await e.write({ collection: FILING_COLLECTION, record: record as unknown as Record<string, unknown>, rkey });
  return { status: "added", filingUri: receipt.uri, did, filingId: input.filingId };
}

export async function listFilings(e: Etzhayyim, input: ListFilingsInput = {}): Promise<ListFilingsOutput> {
  const limit = Math.min(input.limit ?? 50, 200);
  const resp = await e.read<FilingRecord>({ collection: FILING_COLLECTION, cursor: input.cursor, limit });
  const items: FilingView[] = resp.records
    .filter((r) => {
      const v = r.value;
      if (input.entityId && v.entityId !== input.entityId) return false;
      if (input.filingType && v.filingType !== input.filingType) return false;
      if (input.since && v.filedAt < input.since) return false;
      return true;
    })
    .map((r) => ({ ...r.value, filingUri: r.uri }));
  return { items, cursor: resp.cursor, total: items.length };
}

// ─── Ownership edge ─────────────────────────────────────────────────

export async function recordOwnership(e: Etzhayyim, input: RecordOwnershipInput): Promise<RecordOwnershipOutput> {
  if (!input.ownershipId || !input.ownerEntityId || !input.ownedEntityId || !input.sourceUrl) {
    return { status: "rejected", error: "missingRequiredFields" };
  }
  if (input.ownershipPermille != null && !isPermille(input.ownershipPermille)) {
    return { status: "rejected", error: "ownershipPermilleMustBe0to1000" };
  }
  if (input.ownerEntityId === input.ownedEntityId) return { status: "rejected", error: "ownerEqualsOwned" };
  if (!(await exists(e, ENTITY_COLLECTION, entityRkey(input.ownerEntityId)))) {
    return { status: "ownerNotFound", error: `ownerNotFound:${input.ownerEntityId}` };
  }
  if (!(await exists(e, ENTITY_COLLECTION, entityRkey(input.ownedEntityId)))) {
    return { status: "ownedNotFound", error: `ownedNotFound:${input.ownedEntityId}` };
  }
  const rkey = ownershipRkey(input.ownershipId);
  const existing = await e.read<OwnershipRecord>({ collection: OWNERSHIP_COLLECTION, rkey }).catch(() => ({ records: [] }));
  if (existing.records[0]?.value) {
    return { status: "alreadyExists", ownershipUri: existing.records[0].uri, did: existing.records[0].value.did, ownershipId: input.ownershipId };
  }
  const did = ownershipDidFor(input.ownershipId);
  const record: OwnershipRecord = {
    did,
    ownershipId: input.ownershipId,
    ownerEntityId: input.ownerEntityId,
    ownedEntityId: input.ownedEntityId,
    ownershipPermille: input.ownershipPermille,
    sourceUrl: input.sourceUrl,
    createdAt: new Date().toISOString(),
  };
  const receipt = await e.write({ collection: OWNERSHIP_COLLECTION, record: record as unknown as Record<string, unknown>, rkey });
  return { status: "recorded", ownershipUri: receipt.uri, did, ownershipId: input.ownershipId };
}

export async function listOwnership(e: Etzhayyim, input: ListOwnershipInput = {}): Promise<ListOwnershipOutput> {
  const limit = Math.min(input.limit ?? 50, 200);
  const resp = await e.read<OwnershipRecord>({ collection: OWNERSHIP_COLLECTION, cursor: input.cursor, limit });
  const items: OwnershipView[] = resp.records
    .filter((r) => {
      const v = r.value;
      if (input.ownerEntityId && v.ownerEntityId !== input.ownerEntityId) return false;
      if (input.ownedEntityId && v.ownedEntityId !== input.ownedEntityId) return false;
      return true;
    })
    .map((r) => ({ ...r.value, ownershipUri: r.uri }));
  return { items, cursor: resp.cursor, total: items.length };
}

// ─── Coverage ───────────────────────────────────────────────────────

export async function coverage(e: Etzhayyim, input: CoverageInput = {}): Promise<CoverageOutput> {
  const maxScan = Math.min(input.maxScan ?? DEFAULT_MAX_SCAN, DEFAULT_MAX_SCAN);
  const entitiesByJurisdiction: Record<string, number> = {};
  const entitiesByStatus: Record<string, number> = {};
  const entityCount = await scanAll<LegalEntityRecord>(e, ENTITY_COLLECTION, maxScan, (v) => {
    entitiesByJurisdiction[v.jurisdiction] = (entitiesByJurisdiction[v.jurisdiction] ?? 0) + 1;
    entitiesByStatus[v.status] = (entitiesByStatus[v.status] ?? 0) + 1;
  });
  const filingCount = await scanAll<FilingRecord>(e, FILING_COLLECTION, maxScan, () => {});
  const ownershipCount = await scanAll<OwnershipRecord>(e, OWNERSHIP_COLLECTION, maxScan, () => {});
  return {
    entityCount,
    filingCount,
    ownershipCount,
    entitiesByJurisdiction,
    entitiesByStatus,
    truncated: entityCount >= maxScan || filingCount >= maxScan || ownershipCount >= maxScan,
  };
}
