package ca.bc.gov.nrs.hbs.api.catalog;

import ca.bc.gov.nrs.hbs.api.query.CommandDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryCatalog;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter;
import ca.bc.gov.nrs.hbs.api.query.QueryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static checks over every registered screen query / command, so a catalog
 * mistake fails the build instead of a user's search:
 * unique ids, SELECT-only SQL, every filter fragment binds its own param,
 * no literal user-input concatenation markers, client-scope binds correctly.
 */
class CatalogIntegrityTest {

  private static final Pattern BIND = Pattern.compile(":([a-zA-Z][a-zA-Z0-9_]*)");

  static List<QueryCatalog> catalogs() throws Exception {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(QueryCatalog.class));
    List<QueryCatalog> out = new ArrayList<>();
    for (var bd : scanner.findCandidateComponents("ca.bc.gov.nrs.hbs.api")) {
      out.add((QueryCatalog) Class.forName(bd.getBeanClassName()).getDeclaredConstructor().newInstance());
    }
    return out;
  }

  @Test
  void registryLoads_withUniqueIds() throws Exception {
    QueryRegistry registry = new QueryRegistry(catalogs());
    assertThat(registry.allQueries()).isNotEmpty();
  }

  @Test
  void everyQueryIsWellFormed() throws Exception {
    for (QueryCatalog c : catalogs()) {
      for (QueryDefinition q : c.queries()) {
        assertThat(q.sql().stripLeading().toUpperCase()).as(q.id()).startsWith("SELECT");
        assertThat(q.sql()).as(q.id() + " must not end with ;").doesNotEndWith(";");
        assertThat(q.capability()).as(q.id()).isNotNull();
        for (QueryFilter f : q.filters()) {
          assertThat(f.fragment()).as(q.id() + "/" + f.param()).contains(":" + f.param());
          Matcher m = BIND.matcher(f.fragment());
          while (m.find()) {
            String bind = m.group(1);
            assertThat(bind.equals(f.param()) || bind.startsWith("hbs") || bind.equals("scopeClientNumber"))
                .as(q.id() + " filter " + f.param() + " binds foreign :" + bind).isTrue();
          }
        }
        if (q.clientScope() != null) {
          assertThat(q.clientScope()).as(q.id()).contains(":scopeClientNumber");
        }
        q.sortColumns().values().forEach(expr ->
            assertThat(expr).as(q.id() + " sort").doesNotContain(":").doesNotContain(";"));
      }
      for (CommandDefinition cmd : c.commands()) {
        assertThat(cmd.procedure()).as(cmd.id()).matches("[A-Z0-9_$.]+");
        assertThat(cmd.capability()).as(cmd.id()).isNotNull();
      }
    }
  }
}
