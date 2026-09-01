package com.lolfm.application;

import java.util.Optional;

/** Durable checkpoint boundary used only by League-owned Series. */
interface LeagueBoundSeriesPersistencePort {
    void save(SeriesAggregate aggregate);
    Optional<SeriesAggregate> load(String seriesId);
}
