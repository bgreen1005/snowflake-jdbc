/*
 * Copyright (c) 2012-2019 Snowflake Computing Inc. All rights reserved.
 */

package net.snowflake.client.core;

import net.snowflake.client.jdbc.ErrorCode;
import net.snowflake.client.jdbc.SnowflakeUtil;
import net.snowflake.client.log.ArgSupplier;
import net.snowflake.client.log.SFLogger;
import net.snowflake.client.log.SFLoggerFactory;
import net.snowflake.common.core.SFBinary;
import net.snowflake.common.core.SFBinaryFormat;
import net.snowflake.common.core.SFTime;
import net.snowflake.common.core.SFTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.TimeZone;

/**
 * Abstract class used to represent snowflake result set in json format
 */
public abstract class SFJsonResultSet extends SFBaseResultSet
{
  static private final SFLogger logger = SFLoggerFactory.getLogger(
      SFJsonResultSet.class);

  // Timezone used for TimestampNTZ
  private static TimeZone timeZoneUTC = TimeZone.getTimeZone("UTC");

  TimeZone timeZone;

  /**
   * Given a column index, get current row's value as an object
   *
   * @param columnIndex
   * @return
   * @throws SFException
   */
  protected abstract Object getObjectInternal(int columnIndex) throws SFException;

  public Object getObject(int columnIndex) throws SFException
  {
    logger.debug(
        "public Object getObject(int columnIndex)");

    int type = resultSetMetaData.getColumnType(columnIndex);

    Object obj = getObjectInternal(columnIndex);
    if (obj == null)
    {
      return null;
    }

    switch (type)
    {
      case Types.VARCHAR:
      case Types.CHAR:
        return getString(columnIndex);

      case Types.BINARY:
        return getBytes(columnIndex);

      case Types.INTEGER:
        return getInt(columnIndex);

      case Types.DECIMAL:
        return getBigDecimal(columnIndex);

      case Types.BIGINT:
        return getLong(columnIndex);

      case Types.DOUBLE:
        return getDouble(columnIndex);

      case Types.TIMESTAMP:
        return getTimestamp(columnIndex);

      case Types.DATE:
        return getDate(columnIndex);

      case Types.TIME:
        return getTime(columnIndex);

      case Types.BOOLEAN:
        return getBoolean(columnIndex);

      default:
        throw (SFException) IncidentUtil.generateIncidentV2WithException(
            session,
            new SFException(ErrorCode.FEATURE_UNSUPPORTED,
                            "data type: " + type),
            null,
            null);
    }
  }

  @Override
  public String getString(int columnIndex) throws SFException
  {
    logger.debug("public String getString(int columnIndex)");

    // Column index starts from 1, not 0.
    Object obj = getObjectInternal(columnIndex);
    if (obj == null)
    {
      return null;
    }

    // print timestamp in string format
    int columnType = resultSetMetaData.getInternalColumnType(columnIndex);
    switch (columnType)
    {
      case Types.BOOLEAN:
        return ResultUtil.getBooleanAsString(
            ResultUtil.getBoolean(obj.toString()));

      case Types.TIMESTAMP:
      case SnowflakeUtil.EXTRA_TYPES_TIMESTAMP_LTZ:
      case SnowflakeUtil.EXTRA_TYPES_TIMESTAMP_TZ:

        SFTimestamp sfTS = getSFTimestamp(columnIndex);
        int columnScale = resultSetMetaData.getScale(columnIndex);

        String timestampStr = ResultUtil.getSFTimestampAsString(
            sfTS, columnType, columnScale, timestampNTZFormatter,
            timestampLTZFormatter, timestampTZFormatter, session);

        logger.debug("Converting timestamp to string from: {} to: {}",
                     (ArgSupplier) obj::toString, timestampStr);

        return timestampStr;

      case Types.DATE:
        Date date = getDate(columnIndex, timeZoneUTC);

        if (dateFormatter == null)
        {
          throw (SFException) IncidentUtil.generateIncidentV2WithException(
              session,
              new SFException(ErrorCode.INTERNAL_ERROR,
                              "missing date formatter"),
              null,
              null);
        }

        String dateStr = ResultUtil.getDateAsString(date, dateFormatter);

        logger.debug("Converting date to string from: {} to: {}",
                     (ArgSupplier) obj::toString, dateStr);

        return dateStr;

      case Types.TIME:
        SFTime sfTime = getSFTime(columnIndex);

        if (timeFormatter == null)
        {
          throw (SFException) IncidentUtil.generateIncidentV2WithException(
              session,
              new SFException(ErrorCode.INTERNAL_ERROR,
                              "missing time formatter"),
              null,
              null);
        }

        int scale = resultSetMetaData.getScale(columnIndex);
        String timeStr = ResultUtil.getSFTimeAsString(sfTime, scale, timeFormatter);

        logger.debug("Converting time to string from: {} to: {}",
                     (ArgSupplier) obj::toString, timeStr);

        return timeStr;

      case Types.BINARY:
        if (binaryFormatter == null)
        {
          throw (SFException) IncidentUtil.generateIncidentV2WithException(
              session,
              new SFException(ErrorCode.INTERNAL_ERROR,
                              "missing binary formatter"),
              null,
              null);
        }

        if (binaryFormatter == SFBinaryFormat.HEX)
        {
          // Shortcut: the values are already passed with hex encoding, so just
          // return the string unchanged rather than constructing an SFBinary.
          return obj.toString();
        }

        SFBinary sfb = new SFBinary(getBytes(columnIndex));
        return binaryFormatter.format(sfb);

      default:
        break;
    }

    return obj.toString();
  }

  @Override
  public boolean getBoolean(int columnIndex) throws SFException
  {
    logger.debug(
        "public boolean getBoolean(int columnIndex)");

    Object obj = getObjectInternal(columnIndex);
    if (obj == null)
    {
      return false;
    }

    if (obj instanceof Boolean)
    {
      return (Boolean) obj;
    }
    else
    {
      return ResultUtil.getBoolean(obj.toString());
    }
  }

  @Override
  public byte getByte(int columnIndex) throws SFException
  {
    logger.debug("public short getByte(int columnIndex)");

    // Column index starts from 1, not 0.
    Object obj = getObjectInternal(columnIndex);

    if (obj == null)
    {
      return 0;
    }

    if (obj instanceof String)
    {
      return Byte.parseByte((String) obj);
    }
    else
    {
      return ((Number) obj).byteValue();
    }
  }

  @Override
  public short getShort(int columnIndex) throws SFException
  {
    logger.debug("public short getShort(int columnIndex)");

    // Column index starts from 1, not 0.
    Object obj = getObjectInternal(columnIndex);

    if (obj == null)
    {
      return 0;
    }

    if (obj instanceof String)
    {
      return Short.parseShort((String) obj);
    }
    else
    {
      return ((Number) obj).shortValue();
    }
  }

  @Override
  public int getInt(int columnIndex) throws SFException
  {
    logger.debug("public int getInt(int columnIndex)");

    // Column index starts from 1, not 0.
    Object obj = getObjectInternal(columnIndex);

    if (obj == null)
    {
      return 0;
    }

    if (obj instanceof String)
    {
      return Integer.parseInt((String) obj);
    }
    else
    {
      return ((Number) obj).intValue();
    }
  }


  @Override
  public long getLong(int columnIndex) throws SFException
  {
    logger.debug("public long getLong(int columnIndex)");

    // Column index starts from 1, not 0.
    Object obj = getObjectInternal(columnIndex);

    if (obj == null)
    {
      return 0;
    }

    try
    {
      if (obj instanceof String)
      {
        return Long.parseLong((String) obj);
      }
      else
      {
        return ((Number) obj).longValue();
      }
    }
    catch (NumberFormatException nfe)
    {
      int columnType = resultSetMetaData.getColumnType(columnIndex);
      if (Types.INTEGER == columnType
          || Types.SMALLINT == columnType)
      {
        throw (SFException) IncidentUtil.generateIncidentV2WithException(
            session,
            new SFException(ErrorCode.INTERNAL_ERROR,
                            " long: " + obj.toString()),
            null,
            null);
      }
      else
      {
        throw new SFException(ErrorCode.INVALID_VALUE_CONVERT,
                              columnType, "LONG", obj);
      }
    }
  }

  @Override
  public BigDecimal getBigDecimal(int columnIndex) throws SFException
  {
    logger.debug(
        "public BigDecimal getBigDecimal(int columnIndex)");


    // Column index starts from 1, not 0.
    Object obj = getObjectInternal(columnIndex);

    if (obj == null)
    {
      return null;
    }

    return new BigDecimal(obj.toString());
  }

  @Override
  public BigDecimal getBigDecimal(int columnIndex, int scale) throws SFException
  {
    logger.debug(
        "public BigDecimal getBigDecimal(int columnIndex)");


    Object obj = getObjectInternal(columnIndex);

    if (obj == null)
    {
      return null;
    }

    BigDecimal value = new BigDecimal(obj.toString());

    value = value.setScale(scale, RoundingMode.HALF_UP);

    return value;
  }

  private SFTimestamp getSFTimestamp(int columnIndex) throws SFException
  {
    logger.debug(
        "public Timestamp getTimestamp(int columnIndex)");

    Object obj = getObjectInternal(columnIndex);

    if (obj == null)
    {
      return null;
    }

    return ResultUtil.getSFTimestamp(
        obj.toString(),
        resultSetMetaData.getScale(columnIndex),
        resultSetMetaData.getInternalColumnType(columnIndex),
        resultVersion, timeZone, session);
  }

  @Override
  public Time getTime(int columnIndex) throws SFException
  {
    logger.debug("public Time getTime(int columnIndex)");

    int columnType = resultSetMetaData.getColumnType(columnIndex);
    if (Types.TIME == columnType)
    {
      SFTime sfTime = getSFTime(columnIndex);
      if (sfTime == null)
      {
        return null;
      }
      return new Time(sfTime.getFractionalSeconds(ResultUtil.DEFAULT_SCALE_OF_SFTIME_FRACTION_SECONDS));
    }
    else if (Types.TIMESTAMP == columnType)
    {
      return new Time(getTimestamp(columnIndex).getTime());
    }
    else
    {
      throw new SFException(ErrorCode.INVALID_VALUE_CONVERT, columnType, "Time",
                            getObjectInternal(columnIndex));
    }
  }

  @Override
  public Timestamp getTimestamp(int columnIndex, TimeZone tz)
  throws SFException
  {
    int columnType = resultSetMetaData.getColumnType(columnIndex);
    if (Types.TIMESTAMP == columnType)
    {
      SFTimestamp sfTS = getSFTimestamp(columnIndex);

      if (sfTS == null)
      {
        return null;
      }

      Timestamp res = sfTS.getTimestamp();

      if (res == null)
      {
        return null;
      }
      // SNOW-14777: for timestamp_ntz, we should treat the time as in client time
      // zone so adjust the timestamp by subtracting the offset of the client
      // timezone
      if (honorClientTZForTimestampNTZ &&
          resultSetMetaData.getInternalColumnType(columnIndex) == Types.TIMESTAMP)
      {
        res = sfTS.moveToTimeZone(tz).getTimestamp();
      }

      Timestamp adjustedTimestamp = ResultUtil.adjustTimestamp(res);

      return adjustedTimestamp;
    }
    else if (Types.DATE == columnType)
    {
      return new Timestamp(getDate(columnIndex, tz).getTime());
    }
    else if (Types.TIME == columnType)
    {
      return new Timestamp(getTime(columnIndex).getTime());
    }
    else
    {
      throw new SFException(ErrorCode.INVALID_VALUE_CONVERT, columnType, "Timestamp",
                            getObjectInternal(columnIndex));
    }
  }

  @Override
  public float getFloat(int columnIndex) throws SFException
  {
    logger.debug("public float getFloat(int columnIndex)");

    // Column index starts from 1, not 0.
    Object obj = getObjectInternal(columnIndex);

    if (obj == null)
    {
      return 0;
    }

    if (obj instanceof String)
    {
      if ("inf".equals(obj))
      {
        return Float.POSITIVE_INFINITY;
      }
      else if ("-inf".equals(obj))
      {
        return Float.NEGATIVE_INFINITY;
      }
      else
      {
        return Float.parseFloat((String) obj);
      }
    }
    else
    {
      return ((Number) obj).floatValue();
    }
  }

  @Override
  public double getDouble(int columnIndex) throws SFException
  {
    logger.debug("public double getDouble(int columnIndex)");

    // Column index starts from 1, not 0.
    Object obj = getObjectInternal(columnIndex);

    // snow-11974: null for getDouble should return 0
    if (obj == null)
    {
      return 0;
    }

    if (obj instanceof String)
    {
      if ("inf".equals(obj))
      {
        return Double.POSITIVE_INFINITY;
      }
      else if ("-inf".equals(obj))
      {
        return Double.NEGATIVE_INFINITY;
      }
      else
      {
        return Double.parseDouble((String) obj);
      }
    }
    else
    {
      return ((Number) obj).doubleValue();
    }
  }


  @Override
  public byte[] getBytes(int columnIndex) throws SFException
  {
    logger.debug("public byte[] getBytes(int columnIndex)");

    // Column index starts from 1, not 0.
    Object obj = getObjectInternal(columnIndex);

    if (obj == null)
    {
      return null;
    }

    try
    {
      return SFBinary.fromHex(obj.toString()).getBytes();
    }
    catch (IllegalArgumentException ex)
    {
      throw new SFException(ErrorCode.INTERNAL_ERROR,
                            "Invalid binary value: " + obj.toString());
    }
  }

  @Override
  public Date getDate(int columnIndex) throws SFException
  {
    return getDate(columnIndex, TimeZone.getDefault());
  }

  public Date getDate(int columnIndex, TimeZone tz) throws SFException
  {
    if (tz == null)
    {
      tz = TimeZone.getDefault();
    }

    logger.debug("public Date getDate(int columnIndex)");

    // Column index starts from 1, not 0.
    Object obj = getObjectInternal(columnIndex);

    if (obj == null)
    {
      return null;
    }

    int columnType = resultSetMetaData.getColumnType(columnIndex);

    if (Types.TIMESTAMP == columnType)
    {
      return new Date(getTimestamp(columnIndex, tz).getTime());
    }
    else if (Types.DATE == columnType)
    {
      return ResultUtil.getDate(obj.toString(), tz, session);
    }
    // for Types.TIME and all other type, throw user error
    else
    {
      throw new SFException(ErrorCode.INVALID_VALUE_CONVERT, columnType, "DATE", obj);
    }
  }

  private SFTime getSFTime(int columnIndex) throws SFException
  {
    Object obj = getObjectInternal(columnIndex);

    if (obj == null)
    {
      return null;
    }

    int scale = resultSetMetaData.getScale(columnIndex);
    return ResultUtil.getSFTime(obj.toString(), scale, session);
  }

  private Timestamp getTimestamp(int columnIndex) throws SFException
  {
    return getTimestamp(columnIndex, TimeZone.getDefault());
  }
}
