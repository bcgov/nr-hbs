package ca.bc.gov.nrs.hbs.api.workflow;

import ca.bc.gov.nrs.hbs.api.workflow.ProcSignatures.Signature;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Test double for {@link TableApi}: validates every call against the real
 * nr-mof-db signatures (so a wrong parameter name fails the test) and records
 * it instead of reaching Oracle. Sequences count up from 1000; OUT values and
 * function results come from {@link #answer}.
 */
public class RecordingTableApi extends TableApi {

  public record Call(String name, Map<String, Object> args) {
    public Object arg(String param) {
      return args.get(param.toLowerCase());
    }
  }

  public final List<Call> calls = new ArrayList<>();
  private final Map<String, Function<Call, Map<String, Object>>> answers = new LinkedHashMap<>();
  private long sequence = 1000;

  public RecordingTableApi() {
    super(null, ProcSignatures.load());
  }

  /** OUT parameters (or {@code TableApi.RETURN_KEY} for a function) to hand back for a proc. */
  public RecordingTableApi answer(String proc, Function<Call, Map<String, Object>> answer) {
    answers.put(proc.toUpperCase(), answer);
    return this;
  }

  @Override
  public long nextId(String sequenceName) {
    return ++sequence;
  }

  @Override
  protected Map<String, Object> execute(Signature sig, List<Object> values, Integer returnSqlType) {
    Map<String, Object> named = new LinkedHashMap<>();
    for (int i = 0; i < values.size(); i++) {
      named.put(sig.params().get(i).name(), values.get(i));
    }
    Call call = new Call(sig.name(), named);
    calls.add(call);
    var answer = answers.get(sig.name());
    return answer == null ? Map.of() : answer.apply(call);
  }

  public List<Call> callsTo(String proc) {
    return calls.stream().filter(c -> c.name().equalsIgnoreCase(proc)).toList();
  }

  public List<String> names() {
    return calls.stream().map(Call::name).toList();
  }
}
