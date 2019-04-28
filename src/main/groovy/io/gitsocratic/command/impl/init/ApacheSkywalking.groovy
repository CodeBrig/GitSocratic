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
 * Used to initialize the Apache Skywalking service.
 *
 * @version 0.2
 * @since 0.2
 * @author <a href="mailto:brandon.fergerson@codebrig.com">Brandon Fergerson</a>
 */
@ToString(includePackage = false, includeNames = true)
@CommandLine.Command(name = "apache_skywalking",
        description = "Initialize Apache Skywalking service",
        mixinStandardHelpOptions = true,
        descriptionHeading = "%nDescription:%n%n",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n")
class ApacheSkywalking implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", arity = "0", description = "Version to initialize")
    private String skywalkingVersion = defaultApacheSkywalkingVersion

    @CommandLine.Option(names = ["-v", "--verbose"], description = "Verbose logging")
    boolean verbose = Init.defaultVerbose

    @SuppressWarnings("unused")
    protected ApacheSkywalking() {
        //used by Picocli
    }

    ApacheSkywalking(String skywalkingVersion) {
        this.skywalkingVersion = Objects.requireNonNull(skywalkingVersion)
    }

    ApacheSkywalking(String skywalkingVersion, boolean verbose) {
        this.skywalkingVersion = Objects.requireNonNull(skywalkingVersion)
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
        if (Boolean.valueOf(use_docker_apache_skywalking.getValue())) {
            status = initDockerApacheSkywalking()
            if (status != 0) return new InitCommandResult(status)
        } else {
            status = validateExternalApacheSkywalking()
            if (status != 0) return new InitCommandResult(status)
        }
        return new InitCommandResult(status)
    }

    private static int validateExternalApacheSkywalking() {
        println "Validating external Apache Skywalking installation"
        def host = apache_skywalking_host.value
        def port = apache_skywalking_port.value as int
        println " Host: $host"
        println " Port: $port"

        println "Connecting to Apache Skywalking"
        Socket s1 = new Socket()
        try {
            s1.setSoTimeout(200)
            s1.connect(new InetSocketAddress(host, port), 200)
            println "Successfully connected to Apache Skywalking"
            //todo: real connection test
            return 0
        } catch (all) {
            println "Failed to connect to Apache Skywalking"
            all.printStackTrace()
            return -1
        } finally {
            s1.close()
        }
    }

    private int initDockerApacheSkywalking() {
        println "Initializing Apache Skywalking container"
        def callback = new PullImageProgress()
        SocraticCLI.dockerClient.pullImageCmd("apache/skywalking-oap-server:$skywalkingVersion").exec(callback)
        callback.awaitCompletion()

        Container skywalkingContainer
        SocraticCLI.dockerClient.listContainersCmd().withShowAll(true).exec().each {
            if (GitSocraticService.apache_skywalking.command == it.command) {
                skywalkingContainer = it
            }
        }

        if (skywalkingContainer != null) {
            println "Found Apache Skywalking container"
            println " Id: " + skywalkingContainer.id

            //start container (if necessary)
            if (skywalkingContainer.state != "running") {
                println "Starting Apache Skywalking container"
                SocraticCLI.dockerClient.startContainerCmd(skywalkingContainer.id).exec()
                println "Apache Skywalking container started"
            } else {
                println "Apache Skywalking already running"
            }
        } else {
            //create container
            List<Image> images = SocraticCLI.dockerClient.listImagesCmd().withShowAll(true).exec()
            images.each {
                if (it.repoTags?.contains("apache/skywalking-oap-server:$skywalkingVersion")) {
                    def skywalkingPort = apache_skywalking_port.getValue() as int
                    ExposedPort skywalkingTcpPort = ExposedPort.tcp(apache_skywalking_port.defaultValue as int)
                    Ports portBindings = new Ports()
                    portBindings.bind(skywalkingTcpPort, Ports.Binding.bindPort(skywalkingPort))
                    CreateContainerResponse container = SocraticCLI.dockerClient.createContainerCmd(it.id)
                            .withAttachStderr(true)
                            .withAttachStdout(true)
                            .withExposedPorts(skywalkingTcpPort)
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

    static String getDefaultApacheSkywalkingVersion() {
        return "6.0.0-GA"
    }
}