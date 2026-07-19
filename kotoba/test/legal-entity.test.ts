import { describe, it, expect, beforeEach } from "vitest";
import { MockEtzhayyim } from "@etzhayyim/sdk-mock";
import {
  registerEntity,
  setEntityStatus,
  getEntity,
  listEntities,
  addFiling,
  listFilings,
  recordOwnership,
  listOwnership,
  coverage,
} from "../src/index.js";

const SRC = "https://opencorporates.com/example";
const LEI = "353800ABCDEFGHIJ1234";

describe("legal-entity kotoba", () => {
  let e: any;
  beforeEach(() => {
    e = new MockEtzhayyim({ did: "did:web:legal-entity.etzhayyim.com" });
  });

  describe("entity registry", () => {
    it("registers (jurisdiction/LEI validated), reads, lists, status lifecycle", async () => {
      expect((await registerEntity(e, { entityId: "E-1", legalName: "Acme Holdings KK", jurisdiction: "jp", lei: LEI, registrationNumber: "0100-01-000001", sourceRegistry: "JP-NTA" })).status).toBe("registered");
      expect((await getEntity(e, { entityId: "E-1" })).entity?.jurisdiction).toBe("JP");
      expect((await registerEntity(e, { entityId: "E-X", legalName: "x", jurisdiction: "JPN", sourceRegistry: "x" })).status).toBe("rejected"); // 3-letter
      expect((await registerEntity(e, { entityId: "E-Y", legalName: "y", jurisdiction: "JP", lei: "TOOSHORT", sourceRegistry: "x" })).status).toBe("rejected"); // LEI
      await registerEntity(e, { entityId: "E-2", legalName: "Beta Ltd", jurisdiction: "GB", sourceRegistry: "UK-CH" });
      expect((await listEntities(e, { jurisdiction: "JP" })).total).toBe(1);
      expect((await listEntities(e, { q: "acme" })).total).toBe(1);
      expect((await setEntityStatus(e, { entityId: "E-1", status: "dissolved" })).newStatus).toBe("dissolved");
      expect((await setEntityStatus(e, { entityId: "E-1", status: "active" })).status).toBe("rejected"); // dissolved terminal
    });
  });

  describe("filings + ownership graph", () => {
    beforeEach(async () => {
      await registerEntity(e, { entityId: "PARENT", legalName: "Parent Co", jurisdiction: "US", sourceRegistry: "SEC" });
      await registerEntity(e, { entityId: "SUB", legalName: "Sub Co", jurisdiction: "US", sourceRegistry: "SEC" });
    });
    it("adds filings (FK→entity), rejects missing entity", async () => {
      expect((await addFiling(e, { filingId: "F-1", entityId: "PARENT", filingType: "10-K", filedAt: "2026-03-01", sourceUrl: SRC })).status).toBe("added");
      expect((await addFiling(e, { filingId: "F-X", entityId: "GHOST", filingType: "x", filedAt: "x", sourceUrl: SRC })).status).toBe("entityNotFound");
      expect((await listFilings(e, { entityId: "PARENT", filingType: "10-K" })).total).toBe(1);
    });
    it("records ownership edges (both FK), validates per-mille + self-loop", async () => {
      expect((await recordOwnership(e, { ownershipId: "O-1", ownerEntityId: "PARENT", ownedEntityId: "SUB", ownershipPermille: 750, sourceUrl: SRC })).status).toBe("recorded");
      expect((await recordOwnership(e, { ownershipId: "O-X", ownerEntityId: "PARENT", ownedEntityId: "PARENT", sourceUrl: SRC })).status).toBe("rejected"); // self
      expect((await recordOwnership(e, { ownershipId: "O-Y", ownerEntityId: "PARENT", ownedEntityId: "SUB", ownershipPermille: 1500, sourceUrl: SRC })).status).toBe("rejected"); // permille
      expect((await recordOwnership(e, { ownershipId: "O-Z", ownerEntityId: "PARENT", ownedEntityId: "GHOST", sourceUrl: SRC })).status).toBe("ownedNotFound");
      expect((await listOwnership(e, { ownerEntityId: "PARENT" })).total).toBe(1);
      expect((await listOwnership(e, { ownedEntityId: "SUB" })).total).toBe(1);
    });
    it("coverage rolls up entities/filings/ownership", async () => {
      await addFiling(e, { filingId: "F-1", entityId: "PARENT", filingType: "10-K", filedAt: "2026-03-01", sourceUrl: SRC });
      await recordOwnership(e, { ownershipId: "O-1", ownerEntityId: "PARENT", ownedEntityId: "SUB", ownershipPermille: 1000, sourceUrl: SRC });
      const cov = await coverage(e);
      expect(cov.entityCount).toBe(2);
      expect(cov.filingCount).toBe(1);
      expect(cov.ownershipCount).toBe(1);
      expect(cov.entitiesByJurisdiction?.US).toBe(2);
      expect(cov.entitiesByStatus?.active).toBe(2);
    });
  });
});
