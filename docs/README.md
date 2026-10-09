# HBS documentation

Start with the [root README](../README.md). Then:

| Doc | Read it when you need to… |
|---|---|
| [pinch-points.md](pinch-points.md) | understand what makes this modernization hard and what's still open |
| [questions.md](questions.md) | see the decisions blocking parts of the build |
| [architecture.md](architecture.md) | understand the layers, request flow and the legacy → new mapping |
| [screen-framework.md](screen-framework.md) | add or change a screen (frontend registry + backend catalog) |
| [roles-and-security.md](roles-and-security.md) | know the 28 roles, the capability matrix, the client fence and FOI severing |
| [database.md](database.md) | write SQL or call procs against the shared `THE` schema |
| [legacy-logic-to-port.md](legacy-logic-to-port.md) | pick up the remaining business-logic port |
| [batch-jobs.md](batch-jobs.md) | work on scheduled jobs |
| [reports.md](reports.md) | work on the Jasper reports |
| [file-shares-and-storage.md](file-shares-and-storage.md) | deal with files (uploads, archives, delivery) |
| [virus-scanning.md](virus-scanning.md) | configure ClamAV |
| [user-lookup-integration.md](user-lookup-integration.md) | configure nr-user-lookup-api |
| [forest-client-integration.md](forest-client-integration.md) | configure nr-forest-client-api (client picker) |
| [areas/](areas/) | find which new screen/query replaced a legacy P-number (one doc per area) |
| [db/](db/) | DDL this app needs added to nr-mof-db |
