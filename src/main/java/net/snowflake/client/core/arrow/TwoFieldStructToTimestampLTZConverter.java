/*
 * Copyright (c) 2012-2019 Snowflake Computing Inc. All rights reserved.
 */
package net.snowflake.client.core.arrow;

import net.snowflake.client.core.DataConversionContext;
import net.snowflake.client.core.IncidentUtil;
import net.snowflake.client.core.ResultUtil;
import net.snowflake.client.core.SFException;
import net.snowflake.client.jdbc.ErrorCode;
import net.snowflake.client.jdbc.SnowflakeType;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.ValueVector;
import org.apache.arrow.vector.complex.StructVector;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.TimeZone;

/**
 * converter from two-field struct (epochs and fraction) to Timestamp_LTZ
 */
public class TwoFieldStructToTimestampLTZConverter extends AbstractArrowVectorConverter
{
  private StructVector structVector;
  private BigIntVector epochs;
  private IntVector fractions;

  public TwoFieldStructToTimestampLTZConverter(ValueVector fieldVector, int columnIndex, DataConversionContext context)
  {
    super(SnowflakeType.TIMESTAMP_LTZ.name(), fieldVector, columnIndex, context);
    structVector = (StructVector) fieldVector;
    epochs = structVector.getChild(FIELD_NAME_EPOCH, BigIntVector.class);
    fractions = structVector.getChild(FIELD_NAME_FRACTION, IntVector.class);
  }

  @Override
  public boolean isNull(int index)
  {
    return epochs.isNull(index);
  }

  @Override
  public String toString(int index) throws SFException
  {
    if (context.getTimestampLTZFormatter() == null)
    {
      throw (SFException) IncidentUtil.generateIncidentV2WithException(
          context.getSession(),
          new SFException(ErrorCode.INTERNAL_ERROR,
                          "missing timestamp LTZ formatter"),
          null,
          null);
    }
    Timestamp ts = toTimestamp(index, TimeZone.getDefault());

    return ts == null ? null : context.getTimestampLTZFormatter().format(ts, TimeZone.getDefault(),
                                                                         context.getScale(columnIndex));
  }

  @Override
  public Object toObject(int index) throws SFException
  {
    return toTimestamp(index, TimeZone.getDefault());
  }

  @Override
  public Timestamp toTimestamp(int index, TimeZone tz) throws SFException
  {
    return isNull(index) ? null : getTimestamp(index, tz);
  }

  private Timestamp getTimestamp(int index, TimeZone tz) throws SFException
  {
    long epoch = epochs.getDataBuffer().getLong(index * BigIntVector.TYPE_WIDTH);
    int fraction = fractions.getDataBuffer().getInt(index * IntVector.TYPE_WIDTH);

    if (ArrowResultUtil.isTimestampOverflow(epoch))
    {
      return null;
    }

    Timestamp ts = ArrowResultUtil.createTimestamp(epoch, fraction);

    Timestamp adjustedTimestamp = ResultUtil.adjustTimestamp(ts);

    return adjustedTimestamp;
  }

  @Override
  public Date toDate(int index) throws SFException
  {
    if (isNull(index))
    {
      return null;
    }
    Timestamp ts = getTimestamp(index, TimeZone.getDefault());
    // ts can be null when Java's timestamp is overflow.
    return ts == null ? null : new Date(ts.getTime());
  }

  @Override
  public Time toTime(int index) throws SFException
  {
    Timestamp ts = toTimestamp(index, TimeZone.getDefault());
    return ts == null ? null : new Time(ts.getTime());
  }
}
