package io.gitsocratic.command.impl.query

import grakn.client.answer.Numeric
import groovy.transform.ToString
import io.gitsocratic.client.GraknClient
import io.gitsocratic.command.result.QueryCommandResult
import picocli.CommandLine

import java.time.Duration
import java.util.concurrent.Callable

/**
 * Represents the `graql` command.
 * Used to execute a single Graql query.
 *
 * @version 0.2
 * @since 0.1
 * @author <a href="mailto:brandon.fergerson@codebrig.com">Brandon Fergerson</a>
 */
@ToString(includePackage = false, includeNames = true)
@CommandLine.Command(name = "graql",
        description = "Execute a single Graql query",
        mixinStandardHelpOptions = true,
        descriptionHeading = "%nDescription:%n%n",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n")
class Graql implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "Query to run")
    private String query

    @SuppressWarnings("unused")
    protected Graql() {
        //used by Picocli
    }

    Graql(String query) {
        this.query = Objects.requireNonNull(query)
    }

    @Override
    Integer call() throws Exception {
        return executeCommand(true).status
    }

    QueryCommandResult.GraqlResponse execute() throws Exception {
        return executeCommand(false)
    }

    private QueryCommandResult.GraqlResponse executeCommand(boolean outputLogging) throws Exception {
        if (query == null || query.isEmpty()) {
            if (outputLogging) System.err.println("Missing Graql query")
            return new QueryCommandResult.GraqlResponse(-1)
        }

        def startTime = System.currentTimeMillis()
        def graknClient = new GraknClient()
        def tx = graknClient.makeReadSession()
        try {
            if (outputLogging) {
                println "# Query: $query"
                println "# Result:"
            }

            def result = graknClient.executeQuery(tx, query)
            if (result.size() == 1 && result.get(0) instanceof Numeric) {
                if (outputLogging) println((result.get(0) as Numeric).number())
            } else if (!result.isEmpty()) {
                result.each {
                    it.map().forEach({ key, value ->
                        if (outputLogging) println key.toString() + " = " + value.asAttribute().value().toString()
                    })
                }
            } else {
                if (outputLogging) println "N/A"
            }
            if (outputLogging) println "\n# Query time: " + humanReadableFormat(Duration.ofMillis(System.currentTimeMillis() - startTime))
            return new QueryCommandResult.GraqlResponse(0, result)
        } finally {
            tx.close()
            graknClient.close()
        }
    }

    String getQuery() {
        return query
    }

    static String humanReadableFormat(Duration duration) {
        return duration.toString().substring(2)
                .replaceAll('(\\d[HMS])(?!$)', '$1 ')
                .toLowerCase()
    }
}
