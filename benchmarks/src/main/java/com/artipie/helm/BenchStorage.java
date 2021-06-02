package com.artipie.helm;

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ValueNotFoundException;
import com.artipie.asto.memory.InMemoryStorage;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import org.apache.commons.lang3.NotImplementedException;

/**
 * Simple implementation of Storage that holds all data in memory.
 * It does not really delete item from storage. Just marks in map
 * that a specified item was removed.
 *
 * @since 0.3
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public final class BenchStorage implements Storage {
    /**
     * Provided storage implementation.
     */
    private final Storage storage;

    /**
     * Storage content. If value is true, the key exists. Otherwise the key does not exist.
     */
    private final ConcurrentMap<Key, Boolean> existence;

    /**
     * Ctor.
     */
    public BenchStorage() {
        this(new InMemoryStorage());
    }

    /**
     * Ctor.
     * @param storage Storage.
     */
    public BenchStorage(final Storage storage) {
        this.storage = storage;
        this.existence = new ConcurrentHashMap<>();
    }

    @Override
    public CompletableFuture<Boolean> exists(final Key key) {
        return CompletableFuture.supplyAsync(
            () -> !this.absenceOf(key)
        );
    }

    @Override
    public CompletableFuture<Collection<Key>> list(final Key prefix) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public CompletableFuture<Void> save(final Key key, final Content content) {
        return this.storage.save(key, content)
            .thenAccept(noth -> this.existence.put(key, true));
    }

    @Override
    public CompletableFuture<Void> move(final Key source, final Key destination) {
        return CompletableFuture.supplyAsync(
            () -> {
                if (!this.absenceOf(source)) {
                    return this.storage.move(source, destination)
                        .thenAccept(noth -> this.existence.remove(source))
                        .thenAccept(noth -> this.existence.put(destination, true));
                }
                throw new IllegalArgumentException(
                    String.format("No value for source key: %s", source.string())
                );
            }
        ).thenCompose(Function.identity());
    }

    @Override
    public CompletableFuture<Long> size(final Key key) {
        return CompletableFuture.runAsync(
            () -> {
                if (this.absenceOf(key)) {
                    throw new ValueNotFoundException(key);
                }
            }
        ).thenCompose(noth -> this.storage.size(key));
    }

    @Override
    public CompletableFuture<Content> value(final Key key) {
        return CompletableFuture.runAsync(
            () -> {
                if (this.absenceOf(key)) {
                    throw new ValueNotFoundException(key);
                }
            }
        ).thenCompose(noth -> this.storage.value(key));
    }

    @Override
    public CompletableFuture<Void> delete(final Key key) {
        return CompletableFuture.runAsync(
            () -> {
                if (!this.existence.containsKey(key)) {
                    throw new IllegalArgumentException(
                        String.format("Key does not exist: %s", key.string())
                    );
                }
                this.existence.put(key, false);
            }
        );
    }

    @Override
    public <T> CompletionStage<T> exclusively(
        final Key key,
        final Function<Storage, CompletionStage<T>> operation
    ) {
        throw new NotImplementedException("not implemented");
    }

    /**
     * Reset all values of existence map to true.
     * @return Result of completion
     */
    public CompletionStage<Void> reset() {
        return CompletableFuture.runAsync(
            () -> this.existence.replaceAll((key, ignore) -> true)
        );
    }

    /**
     * Is key absent in storage? It means that value from map is null
     * or is equal to false.
     * @param key Key which existence should be checked
     * @return True key is absent in storage, false otherwise.
     */
    private boolean absenceOf(final Key key) {
        return this.existence.get(key) != true;
    }
}
