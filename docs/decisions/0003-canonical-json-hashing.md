# 3. Canonical JSON for policy content hashing

Status: accepted

## Context

Approved policy versions are identified by a SHA-256 content hash. YAML formatting, key order, and whitespace must not change identity. Rule order is part of the policy semantics and must affect the hash.

## Decision

After parsing YAML into the semantic `Policy` model, hash a normalized document (`PolicyHashDocument`) with SHA-256 over UTF-8 bytes from `CanonicalJson` in the `common` module (sorted object keys, compact output, stable numbers).

Do not hash raw YAML bytes.

## Consequences

- Identical semantics produce identical hashes regardless of YAML layout.
- Reordering rules changes the hash even when rule bodies are unchanged.
- Hashing stays centralized in `common`; services reuse the same serializer for audit payloads later.
