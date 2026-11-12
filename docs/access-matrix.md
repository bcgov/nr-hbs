# Access matrix (role × page)

[`access-matrix.xlsx`](access-matrix.xlsx) lists every page in the app and
what each of the 28 person roles can do on it: **R**ead, **W**rite (create),
**U**pdate or **D**elete. [`access-matrix.csv`](access-matrix.csv) is the
main sheet as plain text, so changes show up in a diff.

Both are generated from the code. Don't edit them by hand; regenerate after
any change to screens, actions or capabilities:

```
cd frontend && npm run access:matrix
```

## Proposed role layout

[`access-matrix-proposed.xlsx`](access-matrix-proposed.xlsx) (and
[`.csv`](access-matrix-proposed.csv)) is the same matrix for the 15 person
roles proposed in [role-rationalization.md](role-rationalization.md). It is a
proposal only: the app still runs on the legacy roles.

- Each proposed role gets everything the legacy roles in its "Replaces"
  column grant today, plus read access for its user type, so its cells
  already include R. The mapping is read from that page's tables; change the
  proposal there and regenerate.
- **Changes** sheet: for a person holding each legacy role (with its base role)
  today, every page where the proposed role gives them more or less.
- Deliberate exclusions are in the script's `EXCLUDE` map (today:
  `HBS_SYSTEM_MANAGER` does not take user-data-domain admin from `MOF_MGR`).

## Sheets

| Sheet | Contents |
|---|---|
| Page x role | One row per page (framework screens, hand-built pages, public pages), one column per role. Green = read, amber = create/update, red = delete. |
| Actions | One row per button or command: operation, command id, capability, the roles it resolves to, and a backend cross-check. |
| Roles | Per role: how many pages it can read, create on, update and delete on. |
| Capabilities | Capability → roles, from the backend `Capability.java`. |
| Notes | Legend, caveats, and the consistency checks from the last run. |

## How to read it

- **Each cell is what the role grants on its own.** Roles stack: functional
  roles are held together with a base role (`MOF_USER`, `CLI_USER`,
  `SPC_USER`), which gives the read access. So a functional role column often
  shows `W`/`U`/`D` without `R`.
- **A cell is the most a role can do.** Record state, data domains
  (districts, scale sites, client locations), the industry client fence, and
  per-button rules on summary and detail returns can narrow it. The Actions
  sheet's Notes column says where.
- **The backend decides.** Where a page offers a save to a wider capability
  than the backend command accepts, the cell uses the backend's roles and the
  Notes sheet lists the difference.

## How it is built

`frontend/scripts/build-access-matrix.mjs`:

1. Reads capability → roles from `backend/.../api/query/Capability.java` and
   checks it against the SPA mirror `frontend/src/routes/access.ts`.
2. Loads every screen definition (`frontend/src/screens/areas/*.ts`, public
   screens) and classifies its query (read), form command, links to save
   forms, and record, row and bulk actions (create / update / delete, from the
   command id and button label).
3. Adds the hand-built React pages (`BESPOKE` in the script). Keep that list in
   step with `App.tsx`.
4. Looks up each command's capability in the backend source (catalog
   `CommandDefinition` or `WorkflowCommand.capability()`, including inherited
   ones) and reports differences.

Related: [roles-and-security.md](roles-and-security.md),
[role-rationalization.md](role-rationalization.md).
