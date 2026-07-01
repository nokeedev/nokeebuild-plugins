import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/*private*/ abstract /*final*/ class SourceControlManagementPlugin implements Plugin<Settings> {
	@Inject
	public SourceControlManagementPlugin() {
	}

	@Override
	public void apply(Settings settings) {
		Gradle gradle = settings.getGradle();
		while (gradle.getParent() != null) {
			gradle = gradle.getParent();
		}

		StartParameter rootBuildParameter = gradle.getStartParameter();
		Path vcsCacheDir = Optional.ofNullable(rootBuildParameter.getProjectCacheDir()).orElseGet(() -> new File(rootBuildParameter.getProjectDir(), ".gradle")).toPath().resolve("nokee-vcs");
		settings.getExtensions().create("sourceControlManagement", SourceControlManagementExtension.class, settings, vcsCacheDir);
	}

	public static class SourceControlManagementExtension {
		private static final Logger LOGGER = Logging.getLogger(SourceControlManagementExtension.class);
		private final Settings settings;
		private final Path vcsCacheDir;
		private final ExecOperations execOperations;
		private final ObjectFactory objects;

		@Inject
		public SourceControlManagementExtension(Settings settings, Path vcsCacheDir, ExecOperations execOperations, ObjectFactory objects) {
			this.settings = settings;
			this.vcsCacheDir = vcsCacheDir;
			this.execOperations = execOperations;
			this.objects = objects;
			LOGGER.info(String.format("Using '%s' as VCS cache directory.", vcsCacheDir));
		}

		public void gitRepository(URI uri) {
			gitRepository(uri, __ -> {});
		}

		public void gitRepository(URI uri, Action<? super GitRepo> action) {
			GitRepo git = objects.newInstance(GitRepo.class);
			action.execute(git);
			boolean submodule = git.getSubmodule().getOrElse(false);

			Path repoCacheDir = vcsCacheDir.resolve(hash(uri));
			if (!Files.exists(repoCacheDir)) {
				execOperations.exec(spec -> {
					spec.commandLine("git", "clone", "--depth", "1", "--filter=blob:none");
					if (submodule) {
						spec.args("--recurse-submodules", "--shallow-submodules");
					}
					spec.args(uri, repoCacheDir);
				});
			}

			execOperations.exec(spec -> {
				spec.commandLine("git", "-C", repoCacheDir, "fetch", "--depth", "1", "--filter=blob:none");
				if (submodule) {
					spec.args("--recurse-submodules");
				}
				spec.args("origin");
			});

			execOperations.exec(spec -> {
				spec.commandLine("git", "-C", repoCacheDir, "reset", "--hard", "origin/HEAD");
			});

			if (submodule) {
				execOperations.exec(spec -> {
					spec.commandLine("git", "-C", repoCacheDir, "submodule", "update", "--init", "--recursive", "--depth", "1");
				});
			}

			if (!Files.exists(repoCacheDir)) throw new RuntimeException(String.format("Repo at '%s' was not checked out correctly.", repoCacheDir));
			else LOGGER.info(String.format("Repo '%s' cloned at '%s'", uri, repoCacheDir));
			settings.includeBuild(repoCacheDir.toString());
		}

		public interface GitRepo {
			Property<Boolean> getSubmodule();
		}

		private static String hash(URI uri) {
			try {
				String canonical = uri.normalize().toASCIIString();                // 2. RFC-3986 canonical text
				MessageDigest md = MessageDigest.getInstance("SHA-1"); // 3. SHA-1 digester
				byte[] hash = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
				StringBuilder hex = new StringBuilder(40);
				for (byte b : hash) hex.append(String.format("%02x", b));
				return hex.toString();
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
