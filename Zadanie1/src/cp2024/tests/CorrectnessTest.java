package cp2024.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import cp2024.circuit.Circuit;
import cp2024.circuit.CircuitSolver;
import cp2024.circuit.CircuitValue;
import cp2024.demo.SequentialSolver;
import cp2024.solution.ParallelCircuitSolver;
import cp2024.solution.ParallelCircuitValue;
import cp2024.tests.CircuitGenerator.BalancedRandomCircuitGenerator;
import cp2024.tests.CircuitGenerator.DeepRandomCircuitGenerator;
import cp2024.tests.CircuitGenerator.RandomCircuitGenerator;

public class CorrectnessTest {
  private CircuitSolver sSolver;
  private CircuitSolver pSolver;
  private final int iterations = 20_000;
  private int totalNodes = 5_000;
  private int depthBalancedCircuit = 14;


  @BeforeEach
  void initialize() {
    sSolver = new SequentialSolver();
    pSolver = new ParallelCircuitSolver();
  }

  @AfterEach
  void stop() {
    sSolver.stop();
    pSolver.stop();
  }

  @Test
  void checkRadnom() {
    RandomCircuitGenerator[] generators = {
                                           new DeepRandomCircuitGenerator(totalNodes, new Random()),
                                           new BalancedRandomCircuitGenerator(depthBalancedCircuit, new Random())
                                          };
    for (var generator : generators) {
      for (int i = 0; i < iterations; ++i) {
        if (i % 1000 == 0) {
          System.out.println("DONE " + i +"/ " + iterations);
        }
        generator.setRandom(new Random(i));
        Circuit circuit = generator.generateCircuit().getCircuit();

        CircuitValue sequentialValue =  sSolver.solve(circuit);

        ParallelCircuitValue parallelValue = (ParallelCircuitValue) pSolver.solve(circuit);


        boolean sequential = TestUtils.getVal(sequentialValue);
        boolean parallel = TestUtils.getVal(parallelValue);

        assertEquals(sequential, parallel, "Seed " + i);
      }
    }
  }
}
