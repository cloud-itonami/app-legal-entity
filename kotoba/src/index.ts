/**
 * legal-entity kotoba — barrel.
 *
 * Per ADR-2606011400. Global corporate-registry open-data on the etzhayyim
 * substrate (AT PDS records; no RW).
 *
 *   entity    : registerEntity / setEntityStatus / getEntity / listEntities (q = app-layer search)
 *   filing    : addFiling (FK→entity) / listFilings
 *   ownership : recordOwnership (owner+owned FK→entity, per-mille share) / listOwnership
 *   coverage
 *
 * Public official-registry data; PII (Tier-3) stays in natural-person.
 */

export * from "./types.js";
export {
  registerEntity,
  setEntityStatus,
  getEntity,
  listEntities,
  addFiling,
  listFilings,
  recordOwnership,
  listOwnership,
  coverage,
} from "./registry.js";
