package ru.curs.celesta.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.apache.maven.plugins.annotations.*;

@Mojo(
        name = "gen-cursors",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES
)
public final class GenCursorsMojo extends AbstractGenCursorsMojo {

    @Override
    String getGeneratedSourceDirName() {
        return "generated-sources";
    }

    @Override
    Consumer<String> getAddCompileSourceRootConsumer() {
        return project::addCompileSourceRoot;
    }

    @Override
    Collection<ScoreProperties> getScorePaths() {
        List<ScoreProperties> scorePaths = new ArrayList<>();

        File celestaSqlPath = new File(project.getBasedir(), "src/main/celestasql");
        if (celestaSqlPath.exists()) {
            scorePaths.add(new ScoreProperties(celestaSqlPath.getAbsolutePath()));
        }
        scorePaths.addAll(scores);
        
        return scorePaths;
    }

}
