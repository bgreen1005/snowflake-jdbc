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
import net.snowflake.common.core.SFTimestamp;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.ValueVector;
import org.apache.arrow.vector.complex.StructVector;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.TimeZone;

/**
 * converter from three-field struct (including epoch, fraction, and timezone) to Timestamp_TZ
 */
public class ThreeFieldStructToTimestampTZConverter extends AbstractArrowVectorConverter
{
  private StructVector structVector;
  private BigIntVector epochs;
  private IntVector fractions;
  private IntVector timeZoneIndices;
  private TimeZone timeZone = TimeZone.getTimeZone("UTC");

  public ThreeFieldStructToTimestampTZConverter(ValueVector fieldVector, int columnIndex, DataConversionContext context)
  {
    super(SnowflakeType.TIMESTAMP_LTZ.name(), fieldVector, columnIndex, context);
    structVector = (StructVector) fieldVector;
    epochs = structVector.getChild(FIELD_NAME_EPOCH, BigIntVector.class);
    fractions = structVector.getChild(FIELD_NAME_FRACTION, IntVector.class);
    timeZoneIndices = structVector.getChild(FIELD_NAME_TIME_ZONE_INDEX, IntVector.class);
  }

  @Override
  public String toString(int index) throws SFException
  {
    if (context.getTimestampTZFormatter() == null)
    {
      throw (SFException) IncidentUtil.generateIncidentV2WithException(
          context.getSession(),
          new SFException(ErrorCode.INTERNAL_ERROR,
                          "missing timestamp LTZ formatter"),
          null,
          null);
    }
    Timestamp ts = toTimestamp(index, TimeZone.getDefault());

    return ts == null ? null : context.getTimestampTZFormatter().format(ts,
                                                                        timeZone,
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
    return epochs.isNull(index) ? null : getTimestamp(index, tz);
  }

  private Timestamp getTimestamp(int index, TimeZone tz) throws SFException
  {
    long epoch = epochs.getDataBuffer().getLong(index * BigIntVector.TYPE_WIDTH);
    int fraction = fractions.getDataBuffer().getInt(index * IntVector.TYPE_WIDTH);
    int timeZoneIndex = timeZoneIndices.getDataBuffer().getInt(index * IntVector.TYPE_WIDTH);

    if (ArrowResultUtil.isTimestampOverflow(epoch))
    {
      // Note: this is current behavior for overflowed timestamp
      return null;
    }
    Timestamp ts = ArrowResultUtil.createTimestamp(epoch, fraction);

    if (context.getResultVersion() > 0)
    {
      timeZone = SFTimestamp.convertTimezoneIndexToTimeZone(timeZoneIndex);
    }
    else
    {
      timeZone = TimeZone.getTimeZone("UTC");
    }

    Timestamp adjustedTimestamp = ResultUtil.adjustTimestamp(ts);

    return adjustedTimestamp;
  }

  @Override
  public Date toDate(int index) throws SFException
  {
    if (epochs.isNull(index))
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
