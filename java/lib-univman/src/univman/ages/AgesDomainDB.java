/*
 * $Id$
 *
 * This file is part of the Cloud Services Integration Platform (CSIP),
 * a Model-as-a-Service framework, API, and application suite.
 *
 * 2012-2026, OMSLab, Colorado State University.
 *
 * OMSLab licenses this file to you under the MIT license.
 * See the LICENSE file in the project root for more information.
 */
package univman.ages;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;

/**
 * SWAT+ domain db
 *
 * @author od
 */
public class AgesDomainDB implements AutoCloseable {

  public record OP(String typ, String data1, String data2, String data3, boolean kill_crop) {

  }

  Connection con;

  String GET_OPDATA_QUERY = "SELECT sp_op_typ, sp_op_data1, sp_op_data2, sp_op_data3, op_kill_crop "
      + "FROM d_cr2sp_ops WHERE cr_op_id=?";
  String GET_CROP_QUERY = "SELECT crop_id, sp_crop_id, sp_harv_typ "
      + "FROM d_cr2sp_crops WHERE crop_id=?";
  String GET_FERT_QUERY = "SELECT short_name, inorganic_n, inorganic_p, organic_n, organic_p, solids "
      + "FROM d_manures WHERE short_name=?";

  public static AgesDomainDB create(Connection conn) throws SQLException {
    String dbName = conn.getMetaData().getDatabaseProductName();
    return new AgesDomainDB(conn);
  }

  private AgesDomainDB(Connection con) {
    this.con = con;
  }

  @Override
  public void close() throws Exception {
    if (!con.isClosed())
      con.close();
  }

  public OP getOpData(int opId, String expected_op) {
    try (PreparedStatement ps = con.prepareStatement(GET_OPDATA_QUERY)) {
      ps.setInt(1, opId);
      try (ResultSet result = ps.executeQuery()) {
        if (result.next()) {
          String sp_op_typ = result.getString("sp_op_typ");

          if (!expected_op.equals(sp_op_typ))
            throw new RuntimeException("Expected operation type for op_id '" + opId + "' is '" + expected_op + "', but got: '" + sp_op_typ + "' from domain db.");

          return new OP(
              sp_op_typ,
              result.getString("sp_op_data1"),
              result.getString("sp_op_data2"),
              result.getString("sp_op_data3"),
              result.getInt("op_kill_crop") == 1
          );
        } else
          throw new RuntimeException("No such opId: " + opId);

      } catch (SQLException E) {
        throw new RuntimeException(E);
      }
    } catch (SQLException E) {
      throw new RuntimeException(E);
    }
  }

  /*
    getCrop
   */
  public String[] getCrop(int cropId) {
    try (PreparedStatement ps = con.prepareStatement(GET_CROP_QUERY)) {
      ps.setInt(1, cropId);
      try (ResultSet result = ps.executeQuery()) {
        if (result.next()) {
          String sp_crop_id = result.getString("sp_crop_id");
          if (sp_crop_id == null)
            throw new RuntimeException("No sp_crop_id found for '" + cropId + "' in domain db.");

          return new String[]{
            sp_crop_id,
            result.getString("sp_harv_typ")
          };
        } else
          throw new RuntimeException("No such cropId: " + cropId);

      } catch (SQLException E) {
        throw new RuntimeException(E);
      }
    } catch (SQLException E) {
      throw new RuntimeException(E);
    }
  }

  public double[] getFertFor(String fertName) {
    // min_n min_p	org_n	org_p  = colums / solids
    try (PreparedStatement ps = con.prepareStatement(GET_FERT_QUERY)) {
      ps.setString(1, fertName);
      try (ResultSet result = ps.executeQuery()) {
        if (result.next())
          return new double[]{
            result.getDouble("solids"),
            result.getDouble("inorganic_n"),
            result.getDouble("inorganic_p"),
            result.getDouble("organic_n"),
            result.getDouble("organic_p")
          };
        else
          throw new RuntimeException("No fertilizer info in domain db for: " + fertName);

      } catch (SQLException E) {
        throw new RuntimeException(E);
      }
    } catch (SQLException E) {
      throw new RuntimeException(E);
    }
  }

}
