package io.gitsocratic.command.impl.init

import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Image
import com.github.dockerjava.api.model.Ports
import groovy.transform.ToString
import io.gitsocratic.GitSocraticService
import io.gitsocratic.SocraticCLI
import io.gitsocratic.command.impl.Init
import io.gitsocratic.command.impl.init.docker.PullImageProgress
import io.gitsocratic.command.result.InitCommandResult
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

    @Override
    Integer call() throws Exception {
        return executeCommand(true).status
    }

    InitCommandResult execute() throws Exception {
        return executeCommand(false)
    }

    InitCommandResult executeCommand(boolean outputLogging) throws Exception {
        def status
        if (Boolean.valueOf(use_docker_source_plus_plus.getValue())) {
            status = initDockerSourcePlusPlus()
            if (status != 0) return new InitCommandResult(status)
        } else {
            status = validateExternalSourcePlusPlus()
            if (status != 0) return new InitCommandResult(status)
        }
        return new InitCommandResult(status)
    }

    private static int validateExternalSourcePlusPlus() {
        println "Validating external Source++ installation"
        def host = source_plus_plus_host.value
        def port = source_plus_plus_port.value as int
        println " Host: $host"
        println " Port: $port"

        println "Connecting to Source++"
        Socket s1 = new Socket()
        try {
            s1.setSoTimeout(200)
            s1.connect(new InetSocketAddress(host, port), 200)
            println "Successfully connected to Source++"
            //todo: real connection test
            return 0
        } catch (all) {
            println "Failed to connect to Source++"
            all.printStackTrace()
            return -1
        } finally {
            s1.close()
        }
    }

    private int initDockerSourcePlusPlus() {
        println "Initializing Source++ container"
        def callback = new PullImageProgress()
        SocraticCLI.dockerClient.pullImageCmd("codebrig/source:v$sppVersion-skywalking-h2").exec(callback)
        callback.awaitCompletion()

        Container sppContainer
        SocraticCLI.dockerClient.listContainersCmd().withShowAll(true).exec().each {
            if (GitSocraticService.source_plus_plus.command == it.command) {
                sppContainer = it
            }
        }

        if (sppContainer != null) {
            println "Found Source++ container"
            println " Id: " + sppContainer.id

            //start container (if necessary)
            if (sppContainer.state != "running") {
                println "Starting Source++ container"
                SocraticCLI.dockerClient.startContainerCmd(sppContainer.id).exec()
                println "Source++ container started"
            } else {
                println "Source++ already running"
            }
        } else {
            //create container
            List<Image> images = SocraticCLI.dockerClient.listImagesCmd().withShowAll(true).exec()
            images.each {
                if (it.repoTags?.contains("codebrig/source:v$sppVersion-skywalking-h2")) {
                    def sppPort = source_plus_plus_port.getValue() as int
                    ExposedPort sppTcpPort = ExposedPort.tcp(source_plus_plus_port.defaultValue as int)
                    Ports portBindings = new Ports()
                    portBindings.bind(sppTcpPort, Ports.Binding.bindPort(sppPort))
                    CreateContainerResponse container = SocraticCLI.dockerClient.createContainerCmd(it.id)
                            .withAttachStderr(true)
                            .withAttachStdout(true)
                            .withExposedPorts(sppTcpPort)
                            .withPortBindings(portBindings)
                            .withPublishAllPorts(true)
                            .exec()
                    SocraticCLI.dockerClient.startContainerCmd(container.getId()).exec()
                }
            }
        }
        //todo: real connection test
        return 0
    }

    static String getDefaultSourcePlusPlusVersion() {
        return "0.2.0-alpha"
    }
}