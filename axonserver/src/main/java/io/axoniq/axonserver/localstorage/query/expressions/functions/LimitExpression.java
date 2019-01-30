package io.axoniq.axonserver.localstorage.query.expressions.functions;

import io.axoniq.axonserver.localstorage.query.Expression;
import io.axoniq.axonserver.localstorage.query.ExpressionContext;
import io.axoniq.axonserver.localstorage.query.PipeExpression;
import io.axoniq.axonserver.localstorage.query.Pipeline;
import io.axoniq.axonserver.localstorage.query.QueryResult;

import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Author: marc
 */
public class LimitExpression implements PipeExpression {

    private final Comparator<QueryResult> resultComparator = Comparator.comparing(QueryResult::getId);
    private final long limit;

    public LimitExpression( Expression expression) {
        Number number = (Number) expression.apply(null, null).getValue();
        this.limit = number.longValue();
    }

    @Override
    public List<String> getColumnNames(List<String> inputColumns) {
        return inputColumns;
    }

    @Override
    public boolean process(ExpressionContext context, QueryResult result, Pipeline next) {
        NavigableSet<QueryResult> results = context.scoped(this).computeIfAbsent("results", () -> new ConcurrentSkipListSet<>(resultComparator));
        if (result.isDeleted()) {
            results.remove(result);
        } else {
            results.add(result);
        }
        boolean resultDeleted = false;
        while (results.size() > limit) {
            QueryResult removed = results.pollFirst();
            if (removed == result) {
                resultDeleted = true;
            }
            if (removed != null) {
                next.process(removed.deleted());
            }
        }
        if (!resultDeleted) {
            return next.process(result);
        }
        return results.size() < limit;
    }

}
