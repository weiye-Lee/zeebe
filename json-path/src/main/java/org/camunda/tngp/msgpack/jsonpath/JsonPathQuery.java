package org.camunda.tngp.msgpack.jsonpath;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.msgpack.filter.MsgPackFilter;
import org.camunda.tngp.msgpack.query.MsgPackFilterContext;

public class JsonPathQuery
{
    protected static final int MAX_DEPTH = 30;
    protected static final int MAX_FILTER_CONTEXT_LENGTH = 50;
    protected static final int NO_INVALID_POSITION = -1;

    protected MsgPackFilter[] filters;
    protected MsgPackFilterContext filterInstances = new MsgPackFilterContext(MAX_DEPTH, MAX_FILTER_CONTEXT_LENGTH);

    protected UnsafeBuffer expressionBuffer = new UnsafeBuffer(0, 0);

    protected int invalidPosition;
    protected String errorMessage;

    public JsonPathQuery(MsgPackFilter[] filters)
    {
        this.filters = filters;
    }

    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        filterInstances.clear();
        invalidPosition = NO_INVALID_POSITION;

        expressionBuffer.wrap(buffer, offset, length);
    }

    public MsgPackFilterContext getFilterInstances()
    {
        return filterInstances;
    }

    public MsgPackFilter[] getFilters()
    {
        return filters;
    }

    public void invalidate(int position, String message)
    {
        this.invalidPosition = position;
        this.errorMessage = message;
    }

    public boolean isValid()
    {
        return invalidPosition == NO_INVALID_POSITION;
    }

    public int getInvalidPosition()
    {
        return invalidPosition;
    }

    public String getErrorReason()
    {
        return errorMessage;
    }

    public DirectBuffer getExpression()
    {
        return expressionBuffer;
    }

}
