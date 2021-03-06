/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.providers;

import com.google.common.io.Files;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;
import net.fabricmc.loom.util.ModProcessor;
import net.fabricmc.loom.util.SourceRemapper;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

public class ModRemapperProvider extends DependencyProvider {
	@Override
	public void provide(DependencyInfo dependency, Project project, LoomGradleExtension extension, Consumer<Runnable> postPopulationScheduler) {
		// Provide JAR
		File input = dependency.resolveFile().orElseThrow(() -> new RuntimeException("Could not find dependency " + dependency));

		project.getLogger().lifecycle(":providing " + dependency.getDepString());

		MappingsProvider mappingsProvider = getDependencyManager().getProvider(MappingsProvider.class);
		String verSuffix = ".mapped." + mappingsProvider.mappingsName + "." + mappingsProvider.mappingsVersion;

		String outputNamePrefix = input.getName().substring(0, input.getName().length() - 4) + verSuffix;//TODO use the hash of the input file or something?
		File modStore = extension.getRemappedModCache();
		File output = new File(modStore, outputNamePrefix + ".jar");
		if(output.exists()){
			output.delete();
		}

		ModProcessor.handleMod(input, output, project);

		if(!output.exists()){
			throw new RuntimeException("Failed to remap mod");
		}

		project.getDependencies().add("compile", project.getDependencies().module(
				dependency.getDepString() + verSuffix
		));

		postPopulationScheduler.accept(() -> {
			// Provide sources JAR, if present
			Optional<File> sourcesFile = dependency.resolveFile("sources");
			if (sourcesFile.isPresent()) {
				project.getLogger().lifecycle(":providing " + dependency.getDepString() + " sources");

				try {
					SourceRemapper.remapSources(project, sourcesFile.get(), new File(modStore, outputNamePrefix + "-sources.jar"), true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public String getTargetConfig() {
		return Constants.COMPILE_MODS;
	}
}
