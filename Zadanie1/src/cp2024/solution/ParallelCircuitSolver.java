package cp2024.solution;

import cp2024.circuit.*;
import cp2024.demo.BrokenCircuitValue;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ParallelCircuitSolver implements CircuitSolver {
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean acceptingComputations = true;

    @Override
    public CircuitValue solve(Circuit c) {
        if (!acceptingComputations) {
            return new BrokenCircuitValue();
        }
        AtomicBoolean terminated = new AtomicBoolean(false); // Tworzymy kontekst dla tego układu
        return new ParallelCircuitValue(executor.submit(() -> solveNode(c.getRoot(), terminated, executor)));
    }

    @Override
    public void stop() {
        acceptingComputations = false;
        executor.shutdownNow(); // Zatrzymujemy wszystkie zadania w globalnym executorze
    }

    private boolean solveNode(CircuitNode node, AtomicBoolean terminated , ExecutorService executor) throws InterruptedException {
        if (terminated.get()) {
            throw new InterruptedException("Calculation terminated for this circuit.");
        }
        if (node.getType() == NodeType.IF) {
            return solveNode(node.getArgs()[0], terminated, executor)
                    ? solveNode(node.getArgs()[1], terminated, executor)
                    : solveNode(node.getArgs()[2], terminated, executor);
        }
        return switch (node.getType()) {
            case LEAF -> ((LeafNode) node).getValue();
            case AND -> solveAND(node, terminated, executor);
            case OR -> solveOR(node, terminated, executor);
            case NOT -> !solveNode(node.getArgs()[0], terminated, executor);
            case GT -> solveThreshold(node, terminated, executor, true);
            case LT -> solveThreshold(node, terminated, executor, false);
            default -> throw new RuntimeException("Illegal type: " + node.getType());
        };
    }

    private boolean solveAND(CircuitNode node, AtomicBoolean terminated, ExecutorService executor) throws InterruptedException {
        CircuitNode[] args = node.getArgs();
        BlockingQueue<Future<Boolean>> resultsQueue = submitAll(args, terminated, executor);

        for (int i = 0; i < args.length; i++) {
            try {
                if (!resultsQueue.take().get()) { // Pobieramy wynik w kolejności zakończenia
                    terminated.set(true); // Jeśli znajdziemy `false`, przerywamy inne obliczenia
                    cancelRemaining(resultsQueue); // Anulujemy pozostałe obliczenia
                    return false;
                }
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getCause());
            }
        }
        return true;
    }

    private boolean solveOR(CircuitNode node, AtomicBoolean terminated, ExecutorService executor) throws InterruptedException {
        CircuitNode[] args = node.getArgs();
        BlockingQueue<Future<Boolean>> resultsQueue = submitAll(args, terminated, executor);

        for (int i = 0; i < args.length; i++) {
            try {
                if (resultsQueue.take().get()) { // Pobieramy wynik w kolejności zakończenia
                    terminated.set(true); // Jeśli znajdziemy `true`, przerywamy inne obliczenia
                    return true;
                }
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getCause());
            }
        }
        return false;
    }

    private boolean solveThreshold(CircuitNode node, AtomicBoolean terminated, ExecutorService executor, boolean isGT) throws InterruptedException {
        CircuitNode[] args = node.getArgs();
        int threshold = ((ThresholdNode) node).getThreshold();
        int count = 0;
        BlockingQueue<Future<Boolean>> resultsQueue = submitAll(args, terminated, executor);

        for (int i = 0; i < args.length; i++) {
            try {
                if (resultsQueue.take().get()) { // Pobieramy wynik w kolejności zakończenia
                    count++;
                }
                if (isGT && count > threshold) {
                    terminated.set(true);
                    return true;
                } else if (!isGT && count >= threshold) {
                    terminated.set(true);
                    return false;
                }
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getCause());
            }
        }
        return isGT == (count > threshold);
    }

    private BlockingQueue<Future<Boolean>> submitAll(CircuitNode[] args, AtomicBoolean terminated, ExecutorService executor) {
        BlockingQueue<Future<Boolean>> resultsQueue = new LinkedBlockingQueue<>();
        for (CircuitNode arg : args) {
            resultsQueue.add(executor.submit(() -> solveNode(arg, terminated, executor)));
        }
        return resultsQueue;
    }

    private void cancelRemaining(BlockingQueue<Future<Boolean>> resultsQueue) {
        for (Future<Boolean> future : resultsQueue) {
            future.cancel(true); // Próba anulowania bieżących obliczeń
        }
    }
}

