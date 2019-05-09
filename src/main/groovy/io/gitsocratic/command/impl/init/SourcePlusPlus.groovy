package io.gitsocratic.command.impl.init

import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Image
import com.github.dockerjava.api.model.Ports
import groovy.io.GroovyPrintWriter
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import io.gitsocratic.GitSocraticService
import io.gitsocratic.SocraticCLI
import io.gitsocratic.command.impl.Init
import io.gitsocratic.command.impl.init.docker.PullImageProgress
import io.gitsocratic.command.result.InitCommandResult
import io.gitsocratic.command.result.InitDockerCommandResult
import picocli.CommandLine

import java.util.concurrent.Callable

import static io.gitsocratic.command.config.ConfigOption.*

/**
 * Used to initialize the Source++ service.
 *
 * @version 0.2
 * @since 0.2
 * @author <a href="mailto:brandon.fergerson@codebrig.com">Brandon Fergerson</a>
 */
@Slf4j
@ToString(includePackage = false, includeNames = true)
@CommandLine.Command(name = "source_plus_plus",
        description = "Initialize Source++ service",
        mixinStandardHelpOptions = true,
        descriptionHeading = "%nDescription:%n%n",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n")
class SourcePlusPlus implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", arity = "0", description = "Version to initialize")
    private String sppVersion = defaultSourcePlusPlusVersion

    @CommandLine.Option(names = ["-v", "--verbose"], description = "Verbose logging")
    boolean verbose = Init.defaultVerbose

    boolean useServicePorts = Init.defaultUseServicePorts

    @SuppressWarnings("unused")
    protected SourcePlusPlus() {
        //used by Picocli
    }

    SourcePlusPlus(String sppVersion) {
        this.sppVersion = Objects.requireNonNull(sppVersion)
    }

    SourcePlusPlus(String sppVersion, boolean verbose) {
        this.sppVersion = Objects.requireNonNull(sppVersion)
        this.verbose = verbose
    }

    SourcePlusPlus(String sppVersion, boolean verbose, boolean useServicePorts) {
        this.sppVersion = Objects.requireNonNull(sppVersion)
        this.verbose = verbose
        this.useServicePorts = useServicePorts
    }

    @Override
    Integer call() throws Exception {
        def input = new PipedInputStream()
        def output = new PipedOutputStream()
        input.connect(output)
        Thread.startDaemon {
            input.newReader().eachLine {
                log.info it
            }
        }
        return execute(output).status
    }

    InitCommandResult execute() throws Exception {
        def input = new PipedInputStream()
        def output = new PipedOutputStream()
        input.connect(output)
        return execute(output)
    }

    InitCommandResult execute(PipedOutputStream output) throws Exception {
        def out = new GroovyPrintWriter(output, true)
        if (Boolean.valueOf(use_docker_source_plus_plus.getValue())) {
            try {
                def portBindings = initDockerSourcePlusPlus(out)
                if (portBindings != null) {
                    return new InitDockerCommandResult(portBindings)
                }
            } catch (all) {
                out.println "Failed to initialize service"
                all.printStackTrace(out)
            }
            return new InitDockerCommandResult(-1)
        } else {
            try {
                def status = validateExternalSourcePlusPlus(out)
                return new InitCommandResult(status)
            } catch (all) {
                out.println "Failed to validate external service"
                all.printStackTrace(out)
                return new InitCommandResult(-1)
            }
        }
    }

    private static int validateExternalSourcePlusPlus(PrintWriter out) {
        out.println "Validating external Source++ installation"
        def host = source_plus_plus_host.value
        def port = source_plus_plus_port.value as int
        out.println " Host: $host"
        out.println " Port: $port"

        out.println "Connecting to Source++"
        Socket s1 = new Socket()
        try {
            s1.setSoTimeout(200)
            s1.connect(new InetSocketAddress(host, port), 200)
            out.println "Successfully connected to Source++"
            //todo: real connection test
            return 0
        } catch (all) {
            out.println "Failed to connect to Source++"
            all.printStackTrace(out)
            return -1
        } finally {
            s1.close()
        }
    }

    private Map<String, String[]> initDockerSourcePlusPlus(PrintWriter out) {
        out.println "Initializing Source++ container"
        def callback = new PullImageProgress(out)
        SocraticCLI.dockerClient.pullImageCmd("codebrig/source:v$sppVersion-skywalking-h2").exec(callback)
        callback.awaitCompletion()

        Container sppContainer
        SocraticCLI.dockerClient.listContainersCmd().withShowAll(true).exec().each {
            if (GitSocraticService.source_plus_plus.command == it.command) {
                sppContainer = it
            }
        }

        def containerId
        if (sppContainer != null) {
            containerId = sppContainer.id
            out.println "Found Source++ container"
            out.println " Id: " + sppContainer.id

            //start container (if necessary)
            if (sppContainer.state != "running") {
                out.println "Starting Source++ container"
                SocraticCLI.dockerClient.startContainerCmd(sppContainer.id).exec()
                out.println "Source++ container started"
            } else {
                out.println "Source++ already running"
            }
        } else {
            //create container
            List<Image> images = SocraticCLI.dockerClient.listImagesCmd().withShowAll(true).exec()
            images.each {
                if (it.repoTags?.contains("codebrig/source:v$sppVersion-skywalking-h2")) {
                    ExposedPort sppTcpPort = ExposedPort.tcp(source_plus_plus_port.defaultValue as int)
                    Ports portBindings = new Ports()
                    if (useServicePorts) {
                        portBindings.bind(sppTcpPort, Ports.Binding.bindPort(Integer.parseInt(
                                source_plus_plus_port.defaultValue)))
                    } else {
                        portBindings.bind(sppTcpPort, Ports.Binding.empty())
                    }
                    CreateContainerResponse container = SocraticCLI.dockerClient.createContainerCmd(it.id)
                            .withAttachStderr(true)
                            .withAttachStdout(true)
                            .withExposedPorts(sppTcpPort)
                            .withPortBindings(portBindings)
                            .withPublishAllPorts(true)
                            .exec()
                    SocraticCLI.dockerClient.startContainerCmd(container.getId()).exec()
                    containerId = container.id
                }
            }
        }

        def portBindings = new HashMap<String, String[]>()
        SocraticCLI.dockerClient.inspectContainerCmd(containerId).exec().networkSettings.ports.bindings.each {
            portBindings.put(it.key.toString(), it.value.collect { it.toString() } as String[])
        }
        return portBindings
    }

    static String getDefaultSourcePlusPlusVersion() {
        return "0.2.0-alpha"
    }
}
