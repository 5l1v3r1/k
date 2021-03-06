// Copyright (c) 2018-2019 K Team. All Rights Reserved.
package org.kframework.backend.llvm;

import com.google.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.kframework.backend.llvm.matching.Matching;
import org.kframework.backend.kore.KoreBackend;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.KompileOptions;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.file.FileUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class LLVMBackend extends KoreBackend {

    private final LLVMKompileOptions options;
    private final KExceptionManager kem;
    private final KompileOptions kompileOptions;

    @Inject
    public LLVMBackend(
            KompileOptions kompileOptions,
            FileUtil files,
            KExceptionManager kem,
            LLVMKompileOptions options) {
        super(kompileOptions, files, kem);
        this.options = options;
        this.kompileOptions = kompileOptions;
        this.kem = kem;
    }


    @Override
    public void accept(CompiledDefinition def) {
        String kore = getKompiledString(def);
        files.saveToKompiled("definition.kore", kore);
        FileUtils.deleteQuietly(files.resolveKompiled("dt"));
        MutableInt warnings = new MutableInt();
        Matching.writeDecisionTreeToFile(files.resolveKompiled("definition.kore"), options.heuristic, files.resolveKompiled("dt"), Matching.getThreshold(getThreshold()), options.warnUseless, ex -> {
          kem.addKException(ex);
          warnings.increment();
          return null;
        });
        if (warnings.intValue() > 0 && kem.options.warnings2errors) {
          throw KEMException.compilerError("Had " + warnings.intValue() + " pattern matching errors.");
        }
        if (options.noLLVMKompile) {
            return;
        }
        ProcessBuilder pb = files.getProcessBuilder();
        List<String> args = new ArrayList<>();
        args.add("llvm-kompile");
        args.add("definition.kore");
        args.add("dt");
        args.add("main");
        args.add("-o");
        args.add("interpreter");
        if (kompileOptions.optimize1) args.add("-O1");
        if (kompileOptions.optimize2) args.add("-O2");
        if (kompileOptions.optimize3) args.add("-O2"); // clang -O3 does not make the llvm backend any faster
        args.addAll(options.ccopts);
        try {
            Process p = pb.command(args).directory(files.resolveKompiled(".")).inheritIO().start();
            int exit = p.waitFor();
            if (exit != 0) {
                throw KEMException.criticalError("llvm-kompile returned nonzero exit code: " + exit + "\nExamine output to see errors.");
            }
        } catch (IOException | InterruptedException e) {
            throw KEMException.criticalError("Error with I/O while executing llvm-kompile", e);
        }
    }

    private String getThreshold() {
        if (!options.iterated && !kompileOptions.optimize3) {
            return "0";
        }
        return options.iteratedThreshold;
    }

    @Override
    public Set<String> excludedModuleTags() {
        return new HashSet<>(Arrays.asList("symbolic", "kast"));
    }
}
