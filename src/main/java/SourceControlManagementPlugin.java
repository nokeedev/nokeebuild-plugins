import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/*private*/ abstract /*final*/ class SourceControlManagementPlugin implements Plugin<Settings> {
	@Inject
	public SourceControlManagementPlugin() {
	}

	@Override
	public void apply(Settings settings) {
		// TODO: Use project cache on start parameter instead.
		Gradle gradle = settings.getGradle();
		while (gradle.getParent() != null) {
			gradle = gradle.getParent();
		}

		Path vcsCacheDir = gradle.getStartParameter().getProjectCacheDir().toPath().resolve("nokee-vcs");
		settings.getExtensions().create("sourceControlManagement", SourceControlManagementExtension.class, settings, vcsCacheDir);
	}

	public static class SourceControlManagementExtension {
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
