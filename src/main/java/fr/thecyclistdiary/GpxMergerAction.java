package fr.thecyclistdiary;

import io.quarkiverse.githubaction.Action;
import io.quarkiverse.githubaction.Commands;
import io.quarkiverse.githubaction.Context;
import io.quarkiverse.githubaction.Inputs;
import io.quarkus.logging.Log;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class GpxMergerAction {

    @Action
    void action(Commands commands, Inputs inputs, Context context) throws GitAPIException, IOException {
        String username = inputs.getRequired("username");
        String userToken = inputs.getRequired("user-token");
        String repositoryUrl = inputs.getRequired("git-path");
        String executionFolder = inputs.getRequired("content-path");
        String[] refs = context.getGitHubRef().split("/");
        String branchName = refs[refs.length - 1];
        Path repoDirectory = Files.createTempDirectory("git");
        Path completeExecutionFolder = repoDirectory.resolve(executionFolder);
        CloneCommand cloneCommand = Git.cloneRepository()
                .setBranch(branchName)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, userToken))
                .setDirectory(repoDirectory.toFile())
                .setURI(repositoryUrl);
        try (Git git = cloneCommand.call()) {
            Repository repository = git.getRepository();
            Log.info("Starting analysis of content folder %s".formatted(completeExecutionFolder));
            Set<String> modifiedGpxFiles = GitHelper.getModifiedGpxList(git, repository);
            GpxToMapWalker gpxToMapWalker = new GpxToMapWalker(modifiedGpxFiles);
            Files.walkFileTree(completeExecutionFolder, gpxToMapWalker);
            Log.info("Done analysis of content folder");
            if (!modifiedGpxFiles.isEmpty()) {
                Log.info("Commiting to git repository...");
                GitHelper.commitChanges(git, username, userToken);
            }
            Log.info("Closing program");
            commands.notice("%d files have been updated".formatted(modifiedGpxFiles.size()));
        }
    }
}