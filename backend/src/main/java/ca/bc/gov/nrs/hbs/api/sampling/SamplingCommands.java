package ca.bc.gov.nrs.hbs.api.sampling;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import java.util.Map;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Stratum Planner workflow commands ({@code POST /api/v1/hbs/commands/{id}}),
 * one per legacy P883 / P881 / P886 / P885 / P896 / P897 button. All need
 * {@link Capability#SAMPLING_ADMIN} (HBS_SMP_ADMIN / HBS_CLI_SMP_ADMIN); the
 * district / owner check of {@code SamplePlanFunctionsTag} is made per plan by
 * {@link SamplingAccess}.
 */
@Configuration
public class SamplingCommands {

  /** A workflow bean backed by a service method. */
  record Command(String id, String legacy, Function<Map<String, Object>, Map<String, Object>> fn)
      implements WorkflowCommand {
    @Override
    public Capability capability() {
      return Capability.SAMPLING_ADMIN;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      return fn.apply(body);
    }
  }

  @Bean
  WorkflowCommand samplingPlanCreate(SamplingPlanService s) {
    return new Command("sampling.plan.create", "P891/P892 Add Sampling Plan", s::createPlan);
  }

  @Bean
  WorkflowCommand samplingPlanCalculate(SamplingPlanService s) {
    return new Command("sampling.plan.calculate", "P881 Save & Calculate", s::calculatePlan);
  }

  @Bean
  WorkflowCommand samplingPlanPropose(SamplingPlanService s) {
    return new Command("sampling.plan.propose", "P883/P895 Propose", s::proposePlan);
  }

  @Bean
  WorkflowCommand samplingPlanApprove(SamplingPlanService s) {
    return new Command("sampling.plan.approve", "P883/P895 Approve", s::approvePlan);
  }

  @Bean
  WorkflowCommand samplingPlanReject(SamplingPlanService s) {
    return new Command("sampling.plan.reject", "P883/P895 Reject", s::rejectPlan);
  }

  @Bean
  WorkflowCommand samplingPlanDelete(SamplingPlanService s) {
    return new Command("sampling.plan.delete", "P883/P895 Delete", s::deletePlan);
  }

  @Bean
  WorkflowCommand samplingPlanCopy(SamplingPlanService s) {
    return new Command("sampling.plan.copy", "P896 Copy Sampling Plan", s::copyPlan);
  }

  @Bean
  WorkflowCommand samplingPlanCreateFromActuals(SamplingPlanService s) {
    return new Command("sampling.plan.createFromActuals", "P882/P897 Create New Plan From Actuals",
        s::createPlanFromActuals);
  }

  @Bean
  WorkflowCommand samplingStratumPlanCreate(SamplingPlanService s) {
    return new Command("sampling.stratumPlan.create", "P886 Add Stratum Plan", s::createStratumPlan);
  }

  @Bean
  WorkflowCommand samplingStratumPlanUpdate(SamplingPlanService s) {
    return new Command("sampling.stratumPlan.update", "P886/P887 Edit Stratum Plan", s::updateStratumPlan);
  }

  @Bean
  WorkflowCommand samplingStratumPlanEstimates(SamplingPlanService s) {
    return new Command("sampling.stratumPlan.estimates", "P881 stratum estimates + Save & Calculate",
        s::saveStratumEstimates);
  }

  @Bean
  WorkflowCommand samplingStratumPlanDelete(SamplingPlanService s) {
    return new Command("sampling.stratumPlan.delete", "P886/P898 Delete Stratum Plan", s::deleteStratumPlan);
  }

  @Bean
  WorkflowCommand samplingStratumCompositionCreate(SamplingPlanService s) {
    return new Command("sampling.stratumComposition.create", "P885 Edit Default Ratios (add)",
        s::createComposition);
  }

  @Bean
  WorkflowCommand samplingStratumCompositionUpdate(SamplingPlanService s) {
    return new Command("sampling.stratumComposition.update", "P885 Edit Default Ratios (change)",
        s::updateComposition);
  }

  @Bean
  WorkflowCommand samplingStratumCompositionRemove(SamplingPlanService s) {
    return new Command("sampling.stratumComposition.remove", "P885 Edit Default Ratios (delete)",
        s::removeComposition);
  }
}
