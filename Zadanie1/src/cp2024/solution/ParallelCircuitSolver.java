package cp2024.solution;

import cp2024.circuit.*;
import cp2024.demo.BrokenCircuitValue;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.ArrayList;

public class ParallelCircuitSolver implements CircuitSolver {
    private volatile boolean acceptingComputations = true;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public CircuitValue solve(Circuit c) {
        if (!acceptingComputations) {
            return new BrokenCircuitValue();
        }
        AtomicBoolean terminated = new AtomicBoolean(false);
        Callable<Boolean> task = () -> {
            try {
                return solveNode(c.getRoot(), terminated);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        };
        Future<Boolean> future = executor.submit(task);  // Używamy już współdzielonego ExecutorService
        return new ParallelCircuitValue(future);
    }

    @Override
    public void stop() {
        acceptingComputations = false;
        executor.shutdownNow();
    }

    private boolean solveNode(CircuitNode node, AtomicBoolean terminated) throws InterruptedException {
        if (terminated.get()) {
            throw new InterruptedException("Calculation terminated for this circuit.");
        }

        if (node.getType() == NodeType.IF) {
            return solveNode(node.getArgs()[0], terminated)
                    ? solveNode(node.getArgs()[1], terminated)
                    : solveNode(node.getArgs()[2], terminated);
        }

        return switch (node.getType()) {
            case LEAF -> ((LeafNode) node).getValue();
            case AND -> solveAND(node, terminated);
            case OR -> solveOR(node, terminated);
            case NOT -> !solveNode(node.getArgs()[0], terminated);
            case GT -> solveThreshold(node, terminated, true);
            case LT -> solveThreshold(node, terminated, false);
            default -> throw new RuntimeException("Illegal type: " + node.getType());
        };
    }

    private boolean solveAND(CircuitNode node, AtomicBoolean terminated) throws InterruptedException {
        CircuitNode[] args = node.getArgs();
        List<Thread> threads = new ArrayList<>();
        BlockingQueue<Boolean> resultsQueue = new LinkedBlockingQueue<>();
        for (CircuitNode arg : args) {
            Thread thread = new Thread(() -> {
                try {
                    resultsQueue.add(solveNode(arg, terminated));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (int i = 0; i < args.length; i++) {
            boolean result = resultsQueue.take();
            if (!result) {
                terminated.set(true);
                interruptAll(threads);
                return false;
            }
        }
        return true;
    }

    private boolean solveOR(CircuitNode node, AtomicBoolean terminated) throws InterruptedException {
        CircuitNode[] args = node.getArgs();
        List<Thread> threads = new ArrayList<>();
        BlockingQueue<Boolean> resultsQueue = new LinkedBlockingQueue<>();
        for (CircuitNode arg : args) {
            Thread thread = new Thread(() -> {
                try {
                    resultsQueue.add(solveNode(arg, terminated));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (int i = 0; i < args.length; i++) {
            boolean result = resultsQueue.take();
            if (result) {
                terminated.set(true);
                interruptAll(threads);
                return true;
            }
        }
        return false;
    }

    private boolean solveThreshold(CircuitNode node, AtomicBoolean terminated, boolean isGT) throws InterruptedException {
        CircuitNode[] args = node.getArgs();
        int threshold = ((ThresholdNode) node).getThreshold();
        int count = 0;
        List<Thread> threads = new ArrayList<>();
        BlockingQueue<Boolean> resultsQueue = new LinkedBlockingQueue<>();

        for (CircuitNode arg : args) {
            Thread thread = new Thread(() -> {
                try {
                    resultsQueue.add(solveNode(arg, terminated));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(thread);
            thread.start();
        }

        for (int i = 0; i < args.length; i++) {
            boolean result = resultsQueue.take();
            if (result) {
                count++;
            }
            // PRZECIWN YPRZYPADEK THRESHOLD < COUNT ZE JUZ NIE MOZE BYC
            if (isGT && count > threshold) {
                terminated.set(true);
                interruptAll(threads);
                return true;
            } else if (!isGT && count >= threshold) {
                terminated.set(true);
                interruptAll(threads);
                return false;
            }
        }
        return isGT == (count > threshold);
    }

    private void interruptAll(List<Thread> threads) {
        for (Thread thread : threads) {
            thread.interrupt();
        }
    }
}
