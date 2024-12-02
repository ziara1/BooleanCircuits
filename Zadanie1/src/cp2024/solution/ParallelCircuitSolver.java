package cp2024.solution;

import cp2024.circuit.*;
import cp2024.demo.BrokenCircuitValue;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

// moze dodac sprawdzanie terminated na poczatku kazdego solvera konkretnego
// moze terminated nowe dla kazdego poddrzewa
// moze w ogole bez terminated bedzie dzialalo

public class ParallelCircuitSolver implements CircuitSolver {
    private AtomicBoolean acceptingComputations = new AtomicBoolean(true);
    private List<Thread> threadList = new ArrayList<>();

    @Override
    public synchronized CircuitValue solve(Circuit c) {
        if (!acceptingComputations.get()) {
            return new BrokenCircuitValue();
        }
        ParallelCircuitValue returnValue = new ParallelCircuitValue();
        Thread thread = new Thread(() -> {
            try {
                returnValue.setValue(solveNode(c.getRoot(), new AtomicBoolean(false)));
            } catch (InterruptedException e) {
                returnValue.setException();
                Thread.currentThread().interrupt();
            }
        });
        threadList.add(thread);
        thread.start();
        return returnValue;
    }

    @Override
    public void stop() {
        acceptingComputations.set(false);
        interruptAll(threadList);
    }

    private boolean solveNode(CircuitNode node, AtomicBoolean terminated) throws InterruptedException {
        try {
            if (terminated.get() || !acceptingComputations.get()) {
                Thread.currentThread().interrupt();
                throw new InterruptedException("Calculation terminated for this circuit.");
            }
            terminated = new AtomicBoolean(false);
            return switch (node.getType()) {
                case LEAF -> ((LeafNode) node).getValue();
                case IF -> solveIF(node, terminated);
                case AND -> solveAND(node, terminated);
                case OR -> solveOR(node, terminated);
                case NOT -> !solveNode(node.getArgs()[0], terminated);
                case GT -> solveThreshold(node, terminated, true);
                case LT -> solveThreshold(node, terminated, false);
                default -> throw new RuntimeException("Illegal type: " + node.getType());
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("Calculation terminated for this circuit.");
        }
    }

    private boolean solveIF(CircuitNode node, AtomicBoolean terminated) throws InterruptedException {
        List<Thread> threads = new ArrayList<>();
        try {
            CircuitNode[] args = node.getArgs();
            AtomicReference<Boolean>[] results = new AtomicReference[3];
            CountDownLatch firstLatch = new CountDownLatch(2);
            CountDownLatch secondLatch = new CountDownLatch(3);
            for (int i = 0; i < 3; i++) {
                results[i] = new AtomicReference<>();
                int finalI = i;
                Thread thread = new Thread(() -> {
                    try {
                        results[finalI].set(solveNode(args[finalI], terminated));
                        firstLatch.countDown();
                        secondLatch.countDown();
                    } catch (InterruptedException e) {
                        interruptAll(threads);
                        Thread.currentThread().interrupt();
                    }
                });
                threads.add(thread);
                thread.start();
            }
            try {
                firstLatch.await();
            } catch (InterruptedException e) {
                interruptAll(threads);
                Thread.currentThread().interrupt();
                throw new InterruptedException("Calculation terminated for this circuit.");
            }
            if (results[1].get() != null && results[2].get() != null && results[1].get() == results[2].get()) {
                terminated.set(true); // Przerywamy inne wątki
                threads.get(0).interrupt();
                return results[1].get(); // Gałęzie równe - wynik gotowy
            }
            if (results[0].get() != null) {
                if (results[0].get() && results[1].get() != null) {
                    terminated.set(true); // Przerywamy inne wątki
                    threads.get(2).interrupt();
                    return results[1].get(); // Warunek true, wynik gałęzi "then"
                } else if (!results[0].get() && results[2].get() != null) {
                    terminated.set(true); // Przerywamy inne wątki
                    threads.get(1).interrupt();
                    return results[2].get(); // Warunek false, wynik gałęzi "else"
                }
            }
            try {
                secondLatch.await();
            } catch (InterruptedException e) {
                interruptAll(threads);
                Thread.currentThread().interrupt();
                throw new InterruptedException("Calculation terminated for this circuit.");
            }
            return results[0].get() ? results[1].get() : results[2].get();
        } catch (InterruptedException e) {
            interruptAll(threads);
            Thread.currentThread().interrupt();
            throw new InterruptedException("Calculation terminated for this circuit.");
        }
    }


    private boolean solveAND(CircuitNode node, AtomicBoolean terminated) throws InterruptedException {
        List<Thread> threads = new ArrayList<>();
        try {
            CircuitNode[] args = node.getArgs();
            BlockingQueue<Boolean> resultsQueue = new LinkedBlockingQueue<>();
            for (CircuitNode arg : args) {
                Thread thread = new Thread(() -> {
                    try {
                        resultsQueue.add(solveNode(arg, terminated));
                    } catch (InterruptedException e) {
                        interruptAll(threads);
                        Thread.currentThread().interrupt();
                    }
                });
                threads.add(thread);
                thread.start();
            }
            for (int i = 0; i < args.length; i++) {
                try {
                    boolean result = resultsQueue.take();
                    if (!result) {
                        terminated.set(true);
                        interruptAll(threads);
                        return false;
                    }
                } catch (InterruptedException e) {
                    interruptAll(threads);
                    Thread.currentThread().interrupt();
                    throw new InterruptedException("Calculation terminated for this circuit.");
                }
            }
            return true;
        } catch (InterruptedException e) {
            interruptAll(threads);
            Thread.currentThread().interrupt();
            throw new InterruptedException("Calculation terminated for this circuit.");
        }
    }

    private boolean solveOR(CircuitNode node, AtomicBoolean terminated) throws InterruptedException {
        List<Thread> threads = new ArrayList<>();
        try {
            CircuitNode[] args = node.getArgs();
            BlockingQueue<Boolean> resultsQueue = new LinkedBlockingQueue<>();
            for (CircuitNode arg : args) {
                Thread thread = new Thread(() -> {
                    try {
                        resultsQueue.add(solveNode(arg, terminated));
                    } catch (InterruptedException e) {
                        interruptAll(threads);
                        Thread.currentThread().interrupt();
                    }
                });
                threads.add(thread);
                thread.start();
            }
            for (int i = 0; i < args.length; i++) {
                try {
                    boolean result = resultsQueue.take();
                    if (result) {
                        terminated.set(true);
                        interruptAll(threads);
                        return true;
                    }
                } catch (InterruptedException e) {
                    interruptAll(threads);
                    Thread.currentThread().interrupt();
                    throw new InterruptedException("Calculation terminated for this circuit.");
                }
            }
            return false;
        } catch (InterruptedException e) {
            interruptAll(threads);
            Thread.currentThread().interrupt();
            throw new InterruptedException("Calculation terminated for this circuit.");
        }
    }

    private boolean solveThreshold(CircuitNode node, AtomicBoolean terminated, boolean isGT) throws InterruptedException {
        List<Thread> threads = new ArrayList<>();
        try {
            CircuitNode[] args = node.getArgs();
            int n = args.length;
            int threshold = ((ThresholdNode) node).getThreshold();
            int countP = 0;
            int countN = 0;
            BlockingQueue<Boolean> resultsQueue = new LinkedBlockingQueue<>();
            for (CircuitNode arg : args) {
                Thread thread = new Thread(() -> {
                    try {
                        resultsQueue.add(solveNode(arg, terminated));
                    } catch (InterruptedException e) {
                        interruptAll(threads);
                        Thread.currentThread().interrupt();
                    }
                });
                threads.add(thread);
                thread.start();
            }

            for (int i = 0; i < args.length; i++) {
                try {
                    boolean result = resultsQueue.take();
                    if (result)
                        countP++;
                    else
                        countN++;
                    if (isGT && (countP > threshold || countN >= n - threshold)) {
                        terminated.set(true);
                        interruptAll(threads);
                        return countP > threshold;
                    } else if (!isGT && (countP >= threshold || countN > n - threshold)) {
                        terminated.set(true);
                        interruptAll(threads);
                        return countP < threshold;
                    }
                } catch (InterruptedException e) {
                    interruptAll(threads);
                    Thread.currentThread().interrupt();
                    throw new InterruptedException("Calculation terminated for this circuit.");
                }
            }
            return isGT == (countP > threshold);
        } catch (InterruptedException e) {
            interruptAll(threads);
            Thread.currentThread().interrupt();
            throw new InterruptedException("Calculation terminated for this circuit.");
        }
    }

    private void interruptAll(List<Thread> threads) {
        for (Thread thread : threads) {
            thread.interrupt();
        }
    }
}
