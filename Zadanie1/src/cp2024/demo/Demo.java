package cp2024.demo;

import cp2024.circuit.*;
import cp2024.solution.ParallelCircuitSolver;

import java.time.Duration;

public class Demo {
    public static void main(String[] args) throws InterruptedException {
        CircuitSolver solver = new ParallelCircuitSolver();


        Circuit c = new Circuit(
                CircuitNode.mk(NodeType.IF,
                        CircuitNode.mk(true),
                        CircuitNode.mk(true, Duration.ofSeconds(1)),
                        CircuitNode.mk(false, Duration.ofSeconds(2))
                )
        );

        CircuitValue val2 = solver.solve(c);
        System.out.println("afs");
        System.out.println("value: " + val2.getValue());


        System.out.println("End of demo");
    }
}
