package io.gitsocratic.api.administration.builder

import io.gitsocratic.command.impl.AddLocalRepo

/**
 * Used to construct the 'add-local-repo' command via API.
 *
 * @version 0.2.1
 * @since 0.2
 * @author <a href="mailto:brandon.fergerson@codebrig.com">Brandon Fergerson</a>
 */
class AddLocalRepoCommandBuilder {

    private File repoLocation
    private boolean parallelProcessing = AddLocalRepo.defaultParallelProcessing

    AddLocalRepoCommandBuilder repoLocation(File repoLocation) {
        this.repoLocation = repoLocation
        return this
    }

    AddLocalRepoCommandBuilder parallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing
        return this
    }

    AddLocalRepo build() {
        return new AddLocalRepo(repoLocation, parallelProcessing)
    }
}
