# HBS documentation

Start with the [root README](../README.md). Then:

| Doc | Read it when you need to… |
|---|---|
| [pinch-points.md](pinch-points.md) | understand what makes this modernization hard and what's still open |
| [questions.md](questions.md) | see the decisions blocking parts of the build |
| [architecture.md](architecture.md) | understand the layers, request flow and the legacy → new mapping |
| [screen-framework.md](screen-framework.md) | add or change a screen (frontend registry + backend catalog) |
| [roles-and-security.md](roles-and-security.md) | know the 28 roles, the capability matrix, the client fence and FOI severing |
| [public-access.md](public-access.md) | work on the public (anonymous) read-only area: what is public, its rules, rate limits, adding a public page |
| [database.md](database.md) | write SQL or call procs against the shared `THE` schema |
| [coexistence.md](coexistence.md) | understand how the new UI runs alongside the legacy batch (what runs where, hazards, asks) |
| [workflow-porting.md](workflow-porting.md) | port a legacy workflow or batch job (`TableApi`, `WorkflowCommand`, locking, tests) |
| [legacy-share-bridge.md](legacy-share-bridge.md) | set up the processing-parameter sync to the legacy batch |
| [legacy-logic-to-port.md](legacy-logic-to-port.md) | pick up the remaining business-logic port |
| [batch-jobs.md](batch-jobs.md) | work on scheduled jobs |
| [legacy-batch.md](legacy-batch.md) | understand how the legacy AutoMate/EJB batch worked (diagrams, statuses, schedule) |
| [reports.md](reports.md) | work on the Jasper reports |
| [file-shares-and-storage.md](file-shares-and-storage.md) | deal with files (uploads, archives, delivery) |
| [smbfs-removal.md](smbfs-removal.md) | prepare for the 2026 Q4 Solaris patch that breaks legacy's SMBFS mounts (interim options) |
| [smbfs-option-nfs.md](smbfs-option-nfs.md) / [smbfs-option-nas.md](smbfs-option-nas.md) | run the NFS-from-Windows or shared-NAS fix for the SMBFS removal |
| [virus-scanning.md](virus-scanning.md) | configure ClamAV |
| [user-lookup-integration.md](user-lookup-integration.md) | configure nr-user-lookup-api |
| [forest-client-integration.md](forest-client-integration.md) | configure nr-forest-client-api (client picker) |
| [scale-data-api.md](scale-data-api.md) / [scale-data-api-client-guide.md](scale-data-api-client-guide.md) | work on the REST API that replaces FTP submission and statement pickup (spec + the guide given to clients) |
| [waste-integration-api.md](waste-integration-api.md) | work on the WASTE / Waste Plus billing integration (proposed REST contract) |
| [areas/](areas/) | find which new screen/query replaced a legacy P-number (one doc per area) |
| [db/](db/) | DDL this app needs added to nr-mof-db |
