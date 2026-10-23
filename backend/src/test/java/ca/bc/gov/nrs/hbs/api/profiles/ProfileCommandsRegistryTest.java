package ca.bc.gov.nrs.hbs.api.profiles;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.hbs.api.query.QueryCatalog;
import ca.bc.gov.nrs.hbs.api.query.QueryRegistry;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * The profile workflow beans take over the command ids the SPA already uses
 * (profiles.ts); the registry must load them next to the catalogs without a
 * duplicate id.
 */
class ProfileCommandsRegistryTest extends ProfileFixtures {

  @Test
  void workflowIdsReplaceTheCatalogCommands() throws Exception {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(QueryCatalog.class));
    List<QueryCatalog> catalogs = new ArrayList<>();
    for (var bd : scanner.findCandidateComponents("ca.bc.gov.nrs.hbs.api")) {
      catalogs.add((QueryCatalog) Class.forName(bd.getBeanClassName()).getDeclaredConstructor().newInstance());
    }
    ScaleSiteProfileRules scaleRules = new ScaleSiteProfileRules(reads, caller);
    CruiseBasedRatioRules ratioRules = new CruiseBasedRatioRules(reads);
    List<WorkflowCommand> workflows = List.of(
        new ScaleSiteProfileCreate(api, reads, scaleRules, audit),
        new ScaleSiteProfileUpdate(api, reads, scaleRules, audit),
        new PopulationProfileCreate(api, reads, caller, audit),
        new PopulationProfileUpdate(api, reads, caller, audit),
        new PopulationProfileDelete(api, reads, caller),
        new MarkHolderProfileCreate(api, reads, audit),
        new MarkHolderProfileUpdate(api, reads, audit),
        new MarkHolderProfileDelete(api, reads, audit),
        new DeliveryProfileCreate(api, reads, audit),
        new DeliveryProfileUpdate(api, reads, audit),
        new CruiseBasedRatioCreate(api, ratioRules, audit),
        new CruiseBasedRatioUpdate(api, ratioRules, audit));

    QueryRegistry registry = new QueryRegistry(catalogs, workflows);

    assertThat(registry.allWorkflows()).extracting(WorkflowCommand::id).containsExactlyInAnyOrder(
        "profiles.scaleSites.create", "profiles.scaleSites.update",
        "profiles.population.create", "profiles.population.update", "profiles.population.delete",
        "profiles.markHolders.create", "profiles.markHolders.update", "profiles.markHolders.delete",
        "profiles.delivery.create", "profiles.delivery.update",
        "profiles.cruiseBased.ratio.create", "profiles.cruiseBased.ratio.update");
    assertThat(registry.command("profiles.delivery.delete").procedure()).isEqualTo("HBS_REMOVE_HBS_CLI_DEL_PROFILE");
    assertThat(registry.command("profiles.cruiseBased.ratio.delete").procedure()).isEqualTo("HBS_REMOVE_CB_SP_GRD_RATIO");
    workflows.forEach(w -> assertThat(w.capability()).as(w.id()).isNotNull());
  }
}
