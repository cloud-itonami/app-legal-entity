/**
 * legal-entity kotoba — global corporate-registry open-data record types.
 *
 * Per ADR-2606011400. legal-entity collects + publishes PUBLIC corporate registry
 * data from 194+ jurisdictions (GLEIF LEI / JP NTA / UK CH / SEC EDGAR /
 * OpenCorporates …). This package models the corporate-registry chain:
 *   legalEntity → companyFiling (FK→entity)
 *               → entityOwnership (owner entity → owned entity edge)
 * Registry on AT PDS records (replaces the RW vertex/edge graph). ADR-2605172000
 * kotoba.
 *
 * AXIS NOTE (ADR-2605172400): axis-clean public open-data — official-registry
 * corporate data (LEI / company numbers / ownership structures). No personal PII
 * (PII Tier-3 lives in natural-person, per the app's own topology), no settlement,
 * no fulfillment liability.
 *
 * AT-Lexicon: no float. Ownership percentage is per-mille (0..1000).
 *
 * Identity hierarchy:
 *   did:web:legal-entity.etzhayyim.com                          — controller
 *   did:web:legal-entity.etzhayyim.com:entity:{entityId}        — a legal entity
 *   did:web:legal-entity.etzhayyim.com:filing:{filingId}        — a filing
 *   did:web:legal-entity.etzhayyim.com:owns:{ownershipId}       — an ownership edge
 */

export const LE_DID_PREFIX = "did:web:legal-entity.etzhayyim.com:" as const;

export const ENTITY_COLLECTION = "com.etzhayyim.legalEntity.legalEntity";
export const FILING_COLLECTION = "com.etzhayyim.legalEntity.companyFiling";
export const OWNERSHIP_COLLECTION = "com.etzhayyim.legalEntity.entityOwnership";

// ─── Legal entity ───────────────────────────────────────────────────

export type EntityStatus = "active" | "inactive" | "dissolved";

export interface LegalEntityRecord {
  did: string;
  entityId: string;
  legalName: string;
  /** GLEIF LEI (20 chars), optional. */
  lei?: string;
  /** ISO 3166-1 alpha-2 jurisdiction. */
  jurisdiction: string;
  /** Domestic registration / company number, optional. */
  registrationNumber?: string;
  status: EntityStatus;
  incorporationDate?: string;
  sourceRegistry: string;
  createdAt: string;
}
export interface LegalEntityView extends LegalEntityRecord {
  entityUri: string;
}
export interface RegisterEntityInput {
  entityId: string;
  legalName: string;
  jurisdiction: string;
  sourceRegistry: string;
  lei?: string;
  registrationNumber?: string;
  incorporationDate?: string;
}
export interface RegisterEntityOutput {
  status: "registered" | "alreadyExists" | "rejected";
  entityUri?: string;
  did?: string;
  entityId?: string;
  error?: string;
}
export interface SetEntityStatusInput {
  entityId: string;
  status: EntityStatus;
}
export interface SetEntityStatusOutput {
  status: "updated" | "notFound" | "rejected";
  entityId?: string;
  newStatus?: EntityStatus;
  error?: string;
}
export interface GetEntityInput {
  entityId: string;
}
export interface GetEntityOutput {
  entity?: LegalEntityView;
  error?: string;
}
export interface ListEntitiesInput {
  jurisdiction?: string;
  status?: EntityStatus;
  /** App-layer substring match over legalName (AT PDS has no text search). */
  q?: string;
  limit?: number;
  cursor?: string;
}
export interface ListEntitiesOutput {
  items: LegalEntityView[];
  cursor?: string;
  total: number;
}

// ─── Filing ─────────────────────────────────────────────────────────

export interface FilingRecord {
  did: string;
  filingId: string;
  /** FK → legalEntity entityId. */
  entityId: string;
  filingType: string;
  filedAt: string;
  sourceUrl: string;
  createdAt: string;
}
export interface FilingView extends FilingRecord {
  filingUri: string;
}
export interface AddFilingInput {
  filingId: string;
  entityId: string;
  filingType: string;
  filedAt: string;
  sourceUrl: string;
}
export interface AddFilingOutput {
  status: "added" | "alreadyExists" | "rejected" | "entityNotFound";
  filingUri?: string;
  did?: string;
  filingId?: string;
  error?: string;
}
export interface ListFilingsInput {
  entityId?: string;
  filingType?: string;
  since?: string;
  limit?: number;
  cursor?: string;
}
export interface ListFilingsOutput {
  items: FilingView[];
  cursor?: string;
  total: number;
}

// ─── Ownership edge ─────────────────────────────────────────────────

export interface OwnershipRecord {
  did: string;
  ownershipId: string;
  /** FK → legalEntity entityId (the owner / parent). */
  ownerEntityId: string;
  /** FK → legalEntity entityId (the owned / subsidiary). */
  ownedEntityId: string;
  /** Ownership share, per-mille (0..1000), optional. */
  ownershipPermille?: number;
  sourceUrl: string;
  createdAt: string;
}
export interface OwnershipView extends OwnershipRecord {
  ownershipUri: string;
}
export interface RecordOwnershipInput {
  ownershipId: string;
  ownerEntityId: string;
  ownedEntityId: string;
  sourceUrl: string;
  ownershipPermille?: number;
}
export interface RecordOwnershipOutput {
  status: "recorded" | "alreadyExists" | "rejected" | "ownerNotFound" | "ownedNotFound";
  ownershipUri?: string;
  did?: string;
  ownershipId?: string;
  error?: string;
}
export interface ListOwnershipInput {
  ownerEntityId?: string;
  ownedEntityId?: string;
  limit?: number;
  cursor?: string;
}
export interface ListOwnershipOutput {
  items: OwnershipView[];
  cursor?: string;
  total: number;
}

// ─── Coverage ───────────────────────────────────────────────────────

export interface CoverageInput {
  maxScan?: number;
}
export interface CoverageOutput {
  entityCount?: number;
  filingCount?: number;
  ownershipCount?: number;
  entitiesByJurisdiction?: Record<string, number>;
  entitiesByStatus?: Record<string, number>;
  truncated?: boolean;
  error?: string;
}

// ─── Validation + helpers ───────────────────────────────────────────

export const ENTITY_STATUSES: ReadonlySet<string> = new Set(["active", "inactive", "dissolved"]);

export function isJurisdiction(s: string): boolean {
  return /^[A-Z]{2}$/.test(s);
}
export function isLei(s: string): boolean {
  return /^[A-Z0-9]{20}$/.test(s);
}
export function isPermille(n: unknown): n is number {
  return typeof n === "number" && Number.isInteger(n) && n >= 0 && n <= 1000;
}

export function entityDidFor(id: string): string {
  return `${LE_DID_PREFIX}entity:${id.toLowerCase()}`;
}
export function entityRkey(id: string): string {
  return `entity-${id.toLowerCase()}`;
}
export function filingDidFor(id: string): string {
  return `${LE_DID_PREFIX}filing:${id.toLowerCase()}`;
}
export function filingRkey(id: string): string {
  return `filing-${id.toLowerCase()}`;
}
export function ownershipDidFor(id: string): string {
  return `${LE_DID_PREFIX}owns:${id.toLowerCase()}`;
}
export function ownershipRkey(id: string): string {
  return `owns-${id.toLowerCase()}`;
}
