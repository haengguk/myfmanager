package com.lolfm.application;

import java.util.Optional;

/** Durable checkpoint boundary used only by League-owned Series. */
interface LeagueBoundSeriesPersistencePort {
    default <T>T commandBoundary(SeriesAggregate current,java.util.function.Supplier<T> action) { return action.get(); }
    void save(SeriesAggregate aggregate);
    Optional<SeriesAggregate> load(String seriesId);
}
