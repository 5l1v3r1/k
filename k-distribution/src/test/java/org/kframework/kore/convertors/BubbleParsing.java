package org.kframework.kore.convertors;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.kframework.kil.Definition;
import org.kframework.kil.Sources;
import org.kframework.kore.K;
import org.kframework.kore.convertors.BaseTest.DefintionWithContext;
import org.kframework.kore.outer.Bubble;
import org.kframework.kore.outer.Module;
import org.kframework.kore.outer.Sentence;
import org.kframework.parser.Term;
import org.kframework.parser.TreeNodesToKORE;
import org.kframework.parser.concrete2kore.Grammar;
import org.kframework.parser.concrete2kore.KSyntax2GrammarStatesFilter;
import org.kframework.parser.concrete2kore.TreeCleanerVisitor;
import org.kframework.parser.concrete2kore.Parser;
import org.kframework.parser.outer.Outer;

import static org.kframework.kore.outer.Constructors.*;
import static org.kframework.Collections.*;

/**
 * Takes a KORE module with bubble and returns a new KORE module with all
 * the bubbles parsed.
 *
 * Works for KORE bubbles for now.
 *
 */

class BubbleParsing {

    private Grammar kastGrammar;

    public BubbleParsing(TestParserOnKORE testParserOnKORE) {
        Definition kilDefinitionOfKORE = parseUsingOuter(new File(TestParserOnKORE.ROOT + "/kore.k"));
        KILtoKORE kilToKore1 = new KILtoKORE(null);
        kilDefinitionOfKORE.setMainModule("K");
        org.kframework.kore.outer.Definition definitionOfKORE = kilToKore1.apply(kilDefinitionOfKORE);
        Module kastModule = definitionOfKORE.getModule("K").get();

        kastGrammar = KSyntax2GrammarStatesFilter.getGrammar(kastModule);
    }

    private Definition parseUsingOuter(File file) {
        Definition def = new Definition();
        String definitionText;
        try {
            definitionText = FileUtils.readFileToString(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        def.setItems(Outer.parse(Sources.generatedBy(BubbleParsing.class), definitionText, null));
        def.setMainModule("KAST");
        def.setMainSyntaxModule("KAST");
        return def;
    }

    protected Module parseBubbles(Module m) {
        Set<Module> newImports = stream(m.imports()).map(this::parseBubbles).collect(Collectors.toSet());

        Set<Sentence> newSentences = stream(m.sentences()).map(s -> {
            if (s instanceof Bubble) {
                Bubble bubble = (Bubble) s;

                Parser parser = new Parser(bubble.contents());
                Term parsed = parser.parse(kastGrammar.get("K"), 0);

                TreeCleanerVisitor treeCleanerVisitor = new TreeCleanerVisitor();
                Term cleaned = treeCleanerVisitor.apply(parsed).right().get();

                K kBody = TreeNodesToKORE.apply(cleaned);

                switch (bubble.sentenceType()) {
                case "rule":
                    return Rule(kBody, null, null, bubble.att());
                default:
                    return bubble;
                }
            } else {
                return s;
            }
        }).collect(Collectors.toSet());

        return Module(m.name(), immutable(newImports), immutable(newSentences), m.att());
    }
}