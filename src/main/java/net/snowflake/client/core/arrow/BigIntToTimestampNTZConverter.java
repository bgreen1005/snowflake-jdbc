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
import org.apache.arrow.vector.ValueVector;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.TimeZone;

/**
 * converter from BigInt (Long) to Timestamp_NTZ
 */
public class BigIntToTimestampNTZConverter extends AbstractArrowVectorConverter
{
  private BigIntVector bigIntVector;
  private static final TimeZone NTZ = TimeZone.getTimeZone("UTC");

  public BigIntToTimestampNTZConverter(ValueVector fieldVector, int columnIndex, DataConversionContext context)
  {
    super(SnowflakeType.TIMESTAMP_NTZ.name(), fieldVector, columnIndex, context);
    this.bigIntVector = (BigIntVector) fieldVector;
  }

  @Override
  public String toString(int index) throws SFException
  {
    if (context.getTimestampNTZFormatter() == null)
    {
      throw (SFException) IncidentUtil.generateIncidentV2WithException(
          context.getSession(),
          new SFException(ErrorCode.INTERNAL_ERROR,
                          "missing timestamp NTZ formatter"),
          null,
          null);
    }
    Timestamp ts = isNull(index) ? null : getTimestamp(index, TimeZone.getDefault(), true);

    return ts == null ? null : context.getTimestampNTZFormatter().format(ts,
                                                                         TimeZone.getTimeZone("UTC"),
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
    return isNull(index) ? null : getTimestamp(index, tz, false);
  }

  private Timestamp getTimestamp(int index, TimeZone tz, boolean fromToString) throws SFException
  {
    long val = bigIntVector.getDataBuffer().getLong(index * BigIntVector.TYPE_WIDTH);

    int scale = context.getScale(columnIndex);

    Timestamp ts = ArrowResultUtil.toJavaTimestamp(val, scale);

    // Note: honorClientTZForTimestampNTZ is not enabled for toString method
    if (!fromToString && context.getHonorClientTZForTimestampNTZ())
    {
      ts = ArrowResultUtil.moveToTimeZone(ts, NTZ, tz);
    }

    Timestamp adjustedTimestamp = ResultUtil.adjustTimestamp(ts);

    return adjustedTimestamp;
  }

  @Override
  public Date toDate(int index) throws SFException
  {
    return isNull(index) ? null : new Date(getTimestamp(index, TimeZone.getDefault(), false).getTime());
  }

  @Override
  public Time toTime(int index) throws SFException
  {
    Timestamp ts = toTimestamp(index, TimeZone.getDefault());
    return ts == null ? null : new Time(ts.getTime());
  }
}
