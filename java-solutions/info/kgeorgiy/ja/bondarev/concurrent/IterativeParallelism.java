package info.kgeorgiy.ja.bondarev.concurrent;

import info.kgeorgiy.java.advanced.concurrent.ListIP;
import info.kgeorgiy.java.advanced.concurrent.ScalarIP;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IterativeParallelism implements ScalarIP, ListIP {
    public static <T, S, R> R template(int threadsCount, final List<T> list,
                                    final Function<Stream<T>, S> handler,
                                    final Function<Stream<S>, R> reducer)
            throws InterruptedException {
        threadsCount = Math.min(threadsCount, list.size());
        final int sizeToThread = list.size() / threadsCount;
        final List<S> threadResult = new ArrayList<>(Collections.nCopies(threadsCount, null));
        final List<Thread> threads = new ArrayList<>(threadsCount);
        for (int i = 0; i < threadsCount; i++) {
            final List<T> listToThread = list.subList(i * sizeToThread,
                    i + 1 == threadsCount ? list.size() : (i + 1) * sizeToThread);
            final int threadIter = i;
            threads.add(new Thread(() -> threadResult.set(threadIter, handler.apply(listToThread.stream()))));
            threads.get(i).start();
        }

        final List<InterruptedException> exceptions = new ArrayList<>();
        threads.forEach(x -> {
            try {
                x.join();
            } catch (final InterruptedException exception) {
                exceptions.add(exception);
            }
        });
        final Optional<InterruptedException> exception = exceptions.stream().reduce((a, b) -> {
            a.addSuppressed(b);
            return a;
        });
        if (exception.isPresent()) {
            throw exception.get();
        }

        return reducer.apply(threadResult.stream().filter(Objects::nonNull));
    }

    @Override
    public <T> T maximum(final int threads, final List<? extends T> values, final Comparator<? super T> comparator)
            throws InterruptedException {
        return template(threads, values,
                stream -> stream.max(comparator).orElse(null),
                stream -> stream.max(comparator).orElse(null)
        );
    }

    @Override
    public <T> T minimum(final int threads, final List<? extends T> values, final Comparator<? super T> comparator)
            throws InterruptedException {
        return maximum(threads, values, comparator.reversed());
    }

    @Override
    public <T> boolean all(final int threads, final List<? extends T> values, final Predicate<? super T> predicate)
            throws InterruptedException {
        return template(threads, values,
                stream -> stream.allMatch(predicate),
                stream -> stream.reduce(Boolean::logicalAnd).orElse(Boolean.TRUE)
                );
    }

    @Override
    public <T> boolean any(final int threads, final List<? extends T> values, final Predicate<? super T> predicate)
            throws InterruptedException {
        return !all(threads, values, predicate.negate());
    }

    @Override
    public <T> int count(final int threads, final List<? extends T> values, final Predicate<? super T> predicate)
            throws InterruptedException {
        return template(threads, values,
                stream -> stream.filter(predicate).count(),
                stream -> stream.reduce(Long::sum).orElse(0L)
                ).intValue();
    }

    @Override
    public String join(final int threads, final List<?> values) throws InterruptedException {
        return template(threads, values,
                stream -> stream.map(Objects::toString),
                stream -> stream.flatMap(Function.identity()).collect(Collectors.joining())
        );
    }

    @Override
    public <T> List<T> filter(final int threads, final List<? extends T> values, final Predicate<? super T> predicate)
            throws InterruptedException {
        return template(threads, values,
                stream -> stream.filter(predicate),
                stream -> stream.flatMap(Function.identity()).collect(Collectors.toList())
        );
    }

    @Override
    public <T, U> List<U> map(final int threads, final List<? extends T> values, final Function<? super T, ? extends U> f)
            throws InterruptedException {
        return template(threads, values,
                stream -> stream.map(f),
                stream -> stream.flatMap(Function.identity()).collect(Collectors.toList())
        );
    }
}
