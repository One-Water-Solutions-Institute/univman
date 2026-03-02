/*
 * $Id: 0.18+2 ManagementWriter.java 7228d1c75875 2025-10-31 od $
 *
 * This file is part of the Cloud Services Integration Platform (CSIP),
 * a Model-as-a-Service framework, API, and application suite.
 *
 * 2012-2026, OMSLab, Colorado State University.
 *
 * OMSLab licenses this file to you under the MIT license.
 * See the LICENSE file in the project root for more information.
 */
package univman;

import java.io.File;
import java.io.IOException;

/**
 * Management Writer
 *
 * @author od
 */
public interface ManagementWriter {

  void writeFiles(File dir, String scenario) throws IOException;

}
