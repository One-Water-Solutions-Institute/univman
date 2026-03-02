Universal Management JSON
=========================

Olaf David, Jack Carlson, Frank Geter, Holm Kipka, Ames Fowler

Colorado State University

**Summary**
This document describes the syntax and semantics of a universal JSON format representing management operations in a generic, model independent format and its mapping into model specific inputs for various agro-ecosystem models. Its primary application is the use in web applications that consume JSON input and internally leverage simulation models to quantify soil health within the agricultural domain. Like GeoJSON, a standard for representing and processing geospatial information, the hereby introduced UniManJSON allows for a concise, generic representation of an agricultural management regime and its translation into operational inputs for a range of agricultural simulation models.

.. contents:: Table of Contents

Motivation and Approach
=======================

Environmental simulation models require the input of management information for agricultural field operations such as tillage, planting, harvesting, irrigation, fertilizer application, and many others to model bio-physical processes such as plant growth, plant water uptake, carbon sequestration, and others. Such agro-ecosystem models usually represent the management and operation information in a tailored syntax and semantics as inputs, which limits the transferability and interoperability of such information between models. Model comparison studies are difficult to conduct because of such challenges, even if models are closely conceptually aligned. Here, we introduce an open, structured text format for encoding agricultural management event data for ag-operations and their attributes that allows a model independent representation of management operations from a farmer's perspective. The UniManJSON format is

- **Concise**, since only a minimal set of attributes need to be specified to form a valid event sequence.
- **Transferable**, since such management event data can be exchanged between different systems and applications.
- **Extensible**, since new event types for operation and new mapping to more models can be added without compromising the integrity of existing applications.
- **Web friendly**, since management input can be used in web applications as part of a payload.

The key components of UniManJSON and their interaction as shown in Figure 1. There are four major components involved:

- **Universal Management JSON**: represents a farmer's perspective on management events that happen on a given field location over time. This is expressed in JSON.
- **Domain Table(s)**: contain static mapping information from generic management operations to specific operations that can be understood by a given model. Domain tables are usually stored in a database.
- **Translator**: performs the translation of the UniManJson using the Domain Table(s) into operational input files for a model.
- **Model Input Files**: represent valid input files of an Agro-Ecosystem Model with the respective management information.


The universal management operation object contains a series of events representing the farming operations during the cropping sequence. Operation and crop identifiers, as well as name and other associated data, come from the NRCS Conservation Resources Land Management Operations Database (CR_LMOD). This database contains 160+ crops, 450+ farming operations, and 25,000+ management templates across 75 crop management zones ([#ftm]_)



* *Step 1:* Define a JSON Universal management.

  .. code-block:: java

      String manStr = """
          {
              "management": {
                "name": "Corn CT",
                "events": [
                  {
                    "date": "0000-05-05",
                    "name": "Planter, double disk opnr",
                    "type": "op-plant",
                    "op_id": 22922,
                    // ...
                  },
                  // more events
                ]
              }
          }
      """

* *Step 2:* Create a Management list

  .. code-block:: java

     // Load management
     List<UniManagement> uMans = UniManagement.from(manStr);


* *Step 3:* Transform the management into an event sequence, unroll rotations if needed

  .. code-block:: java

    // create sequence
    List<UniEvent> uEvents = UniManagement.createEventSequence(uMans, "2008-01-01", "2019-12-31");


* *Step 4:* Translate the event sequence into a model specific management object using a domain table.

  .. code-block:: java

      try (SwatPlusDomainDB db = SwatPlusDomainDB.create(dbConnection)) {
        // map Universal Events to SWAT+ Management Events
        SwatPlusManagement spm = SwatPlusManagement.from(uEvents, "mgt_01", db);


* *Step 5:* Write the model management input(s).

  .. code-block:: java

        spm.writeFiles(dir, "scenario1");
      }


UniManJSON
==========

A Universal management is encoded as JSON text. A management can be an array of individual managements or just a single management. In the example below the management list as JSON Array or the single management as JSON Object are values to a management field. This name is arbitrary and may be vary by application context.

.. code-block::  json

    {
      "managements": [    //  list of single managements
        {
          // single management
        },
        {
          // single management
        }
      ]
    }



.. code-block::  json

    {
      "management": {
        // single management
      }
    }


* A Management list can have one or more management objects
* A Management object can have one or more management events representing an operation sequence


Management Object
-----------------

The table below shows the fields for a single management object.

===================   =============================================================
Description:          The sequence of operation events for a single management.
Type:                 JSON
Fields:
 ``"name"``           The management name as string. (*optional*)

 ``"until_end_of"``   The year that this management applies until, starting at the
                      beginning of the simulation. If specified, the year must be
                      within the simulation dates. (*optional*)

 ``"events"``         The array of events of this management, See next section for
                      detailed description of the events. (*required*)


Example:              .. code-block::  json

                        {
                          "name": "Corn CT",
                          "until_end_of": 2007,
                          "events": [
                              // list of events
                          ]
                        }


Notes:                * The service will error out if the provided date values
                        are not in range,are invalid,or specify an invalid period.
===================   =============================================================


All management events follow a defined structure and have required fields as shown in the table below.


==================  =============================================================
Description:        The sequence of operation events for a single management.
Type:               JSON
Fields:
 ``"date"``         The date (ISO 8601 date format) of the event, with the year
                    being absolute or a relative offset to the simulation start year
                    in simdates (see below for more information) (*required*)

 ``"type"``         The operation type, string, starting with ``op-``, e.g.
                    ``op-tillage`` The operation type should be lowercase, the type
                    can have the ``-`` or an ``_`` in its name as a separator. Therefore,
                    ``op-tillage`` and ``op_tillage`` are both valid operation types. (*required*)

 ``"name"``         The operation name/description, string, (*required*)

 ``"op_id"``        The operation id in CR_LMOD, decimal number. (*required*)


Example:            .. code-block::  json

                     {
                        "date": "0000-05-05",
                        "name": "Planter, double disk opnr",
                        "type": "op-plant",
                        "op_id": 22922,
                        // ...
                      }

                      // - or -

                      {
                        "date": "2020-04-15",
                        "name": "Planter, double disk opnr",
                        "type": "op-plant",
                        "op_id": 22922,
                        // ...
                      }



Notes:              * The service will error out if the provided date values
                      are not in range,are invalid,or specify an invalid period.
                    * Specific events must always have the 4 fields, other fields
                      might be required based on the operation type
==================  =============================================================


Event dates (``“date”``) can be provided with absolute or relative years for a given management. They cannot be mixed within a single management, but multiple managements in a payload can have different year representation. The first event in a management defines if the entire event sequence is either **absolute** or **relative**.

**Absolute**
  The year specified in a date is a valid year that can be directly used. An example is ``“2016-05-01”``. The absolute date must be within the simulation dates.

**Relative**
  The year specified in a date is relative to the simulation start date. An example is ``"0000-05-01"``. Here, the year ``“0000”`` is not a valid year on its own. With a start year of 2012 provided in simdates (e.g. [``“2012-01-01”``, ``“2023-12-31”``]), the service will adjust the event date internally to  ``“2012-05-01”``. Therefore the relative year can be seen as a year offset. It should start with ``"0000"`` for the first event in the first year.

Relative years in dates can be used for specifying management rotations over the period of the simulation or until a certain year (``“until_end_of”``) within that simulation period.

For example, if a management has 10 relative events for a 2 year rotation specified at ``“0000-04-01”``, ``“0000-05-15”``, ``“0000-05-16”``, ``“0000-10-15”``, ``“0001-04-01”``, ``“0001-04-15”``, ``“0001-05-15”``, ``“0001-05-16”``, ``“0001-10-15”``, ``“0001-12-01”``, and the simulation period (simdates) starts in year 2005, those events will be adjusted to  ``“2005-04-01”``, ``“2005-05-15”``, ``“2005-05-16”``, ``“2005-10-15”``, ``“2006-04-01”``, ``“2006-04-15”``, ``“2006-05-15”``, ``“2006-05-16”``, ``“2006-10-15”``, ``“2006-12-01”``. Furthermore, if the management contains an ``“until_end_of”`` year, the two year rotation will be repeated until that year with the dates adjusted accordingly.  If ``“until_end_of”`` is not present, the end of the simulation period as provided in simdates is used.




Event type: ``op-plant``
------------------------


================  ============================================================
Type:             ``"op-plant"``
Description:      Describes a planting operation.
Add. Fields:
 ``"crop_id"``    The crop to plant (CR_LMOD id), *(required)*
 ``"crop_name"``  The crop name, *(optional)*
 ``"irr"``        Indicator if the crop gets irrigated, must
                  be ``true`` or ``false``, *(optional, defaults to ``false`` if missing)*.

                  It will draw from the annual available water for irrigation as specified in ``op-irrigation-allocation`` operation. If present, the model will start irrigating at planting date and will stop irrigating 14 days before harvest. It will use ‘sprinkler\_med’ irrigation with 1.5 inches applied at each irrigation. The total annual irrigation amount will be capped by the value in ``op-irrigation-allocation``.
Example:          .. code-block::  json

                      {
                        "date": "0000-10-07",
                        "name": "Harvest, killing crop 50pct standing stubble",
                        "type": "op-harvest",
                        "op_id": 22822,
                        "crop_name": "Corn",
                        "crop_id": 763
                      }

Notes:             * The service will error out if the provided date values are not in range, are invalid, or specify an invalid period.
                   * If the operation has the ``irr`` field set to ``true`` but there is no ``op-irrigation-allocation`` event for the same year, the service will fail with an error.
================  ============================================================




Event type: ``op-harvest``
--------------------------


================  ============================================================
Type:             ``"op-harvest"``
Description:      Describes a harvesting operation. The CR_LMOD operation
                  record might lead to the generate of a subsequent kill operation
Fields:
 ``"crop_id"``     The crop to harvest (CR_LMOD id), *(required)*
 ``"crop_name"``   The crop name, *(optional)*
Example:          .. code-block::  json

                      {
                        "date": "0000-10-07",
                        "name": "Harvest, killing crop 50pct standing stubble",
                        "type": "op-harvest",
                        "op_id": 22822,
                        "crop_name": "Corn",
                        "crop_id": 763
                      }


Notes:             *  The service will error out if the provided date values are not
                      in range, are invalid, or specify an invalid period.
================  ============================================================



Event type: ``op-amendment-fertilizer``
---------------------------------------

====================  ============================================================
Type:                 ``"op-amendment-fertilizer"``
Description:          Describes a fertilizer application operation.
Fields:
 ``"min_n_lbs_ac"``   Mineralized nitrogen amount applied in lbs/ac *(optional)*
 ``"min_p_lbs_ac"``   Mineralized phosphorus amount applied in lbs/ac *(optional)*
 ``"fert_name"``      Fertilizer product applied *(optional)*; currently constrained to FtM manures.
                      Valid inputs values for fert_name are the FtM short names below:

                      * ``"mw_bf_lq"`` - Midwest_Beef_Liquid
                      * ``"mw_bf_sl"`` - Midwest_Beef_Slurry
                      * ``"mw_bf_ss"`` - Midwest_Beef_Semi-Solid
                      * ``"mw_bf_sd"`` - Midwest_Beef_Solid
                      * ``"mw_da_lq"`` - Midwest_Dairy_Liquid
                      * ``"mw_da_sl"`` - Midwest_Dairy_Slurry
                      * ``"mw_da_ss"`` - Midwest_Dairy_Semi-Solid
                      * ``"mw_da_sd"`` - Midwest_Dairy_Solid
                      * ``"mw_po_lq"`` - Midwest_Poultry_Liquid
                      * ``"mw_po_sl"`` - Midwest_Poultry_Slurry
                      * ``"mw_po_ss"`` - Midwest_Poultry_Semi-Solid
                      * ``"mw_po_sd"`` - Midwest_Poultry_Solid
                      * ``"mw_sw_lq"`` - Midwest_Swine_Liquid
                      * ``"mw_sw_sl"`` - Midwest_Swine_Slurry
                      * ``"mw_sw_ss"`` - Midwest_Swine_Semi-Solid
                      * ``"mw_sw_sd"`` - Midwest_Swine_Solid
                      * ``"ne_bf_lq"`` - Northeast_Beef_Liquid
                      * ``"ne_bf_sl"`` - Northeast_Beef_Slurry
                      * ``"ne_bf_ss"`` - Northeast_Beef_Semi-Solid
                      * ``"ne_bf_sd"`` - Northeast_Beef_Solid
                      * ``"ne_da_lq"`` - Northeast_Dairy_Liquid
                      * ``"ne_da_sl"`` - Northeast_Dairy_Slurry
                      * ``"ne_da_ss"`` - Northeast_Dairy_Semi-Solid
                      * ``"ne_da_sd"`` - Northeast_Dairy_Solid
                      * ``"ne_po_lq"`` - Northeast_Poultry_Liquid
                      * ``"ne_po_sl"`` - Northeast_Poultry_Slurry
                      * ``"ne_po_ss"`` - Northeast_Poultry_Semi-Solid
                      * ``"ne_po_sd"`` - Northeast_Poultry_Solid
                      * ``"ne_sw_lq"`` - Northeast_Swine_Liquid
                      * ``"ne_sw_sl"`` - Northeast_Swine_Slurry
                      * ``"ne_sw_ss"`` - Northeast_Swine_Semi-Solid
                      * ``"ne_sw_sd"`` - Northeast_Swine_Solid
                      * ``"np_bf_lq"`` - Northern Plains_Beef_Liquid
                      * ``"np_bf_sl"`` - Northern Plains_Beef_Slurry
                      * ``"np_bf_ss"`` - Northern Plains_Beef_Semi-Solid
                      * ``"np_bf_sd"`` - Northern Plains_Beef_Solid
                      * ``"np_da_lq"`` - Northern Plains_Dairy_Liquid
                      * ``"np_da_sl"`` - Northern Plains_Dairy_Slurry
                      * ``"np_da_ss"`` - Northern Plains_Dairy_Semi-Solid
                      * ``"np_da_sd"`` - Northern Plains_Dairy_Solid
                      * ``"np_po_lq"`` - Northern Plains_Poultry_Liquid
                      * ``"np_po_sl"`` - Northern Plains_Poultry_Slurry
                      * ``"np_po_ss"`` - Northern Plains_Poultry_Semi-Solid
                      * ``"np_po_sd"`` - Northern Plains_Poultry_Solid
                      * ``"np_sw_lq"`` - Northern Plains_Swine_Liquid
                      * ``"np_sw_sl"`` - Northern Plains_Swine_Slurry
                      * ``"np_sw_ss"`` - Northern Plains_Swine_Semi-Solid
                      * ``"np_sw_sd"`` - Northern Plains_Swine_Solid
                      * ``"nw_bf_lq"`` - Pacific Northwest_Beef_Liquid
                      * ``"nw_bf_sl"`` - Pacific Northwest_Beef_Slurry
                      * ``"nw_bf_ss"`` - Pacific Northwest_Beef_Semi-Solid
                      * ``"nw_bf_sd"`` - Pacific Northwest_Beef_Solid
                      * ``"nw_da_lq"`` - Pacific Northwest_Dairy_Liquid
                      * ``"nw_da_sl"`` - Pacific Northwest_Dairy_Slurry
                      * ``"nw_da_ss"`` - Pacific Northwest_Dairy_Semi-Solid
                      * ``"nw_da_sd"`` - Pacific Northwest_Dairy_Solid
                      * ``"nw_po_lq"`` - Pacific Northwest_Poultry_Liquid
                      * ``"nw_po_sl"`` - Pacific Northwest_Poultry_Slurry
                      * ``"nw_po_ss"`` - Pacific Northwest_Poultry_Semi-Solid
                      * ``"nw_po_sd"`` - Pacific Northwest_Poultry_Solid
                      * ``"nw_sw_lq"`` - Pacific Northwest_Swine_Liquid
                      * ``"nw_sw_sl"`` - Pacific Northwest_Swine_Slurry
                      * ``"nw_sw_ss"`` - Pacific Northwest_Swine_Semi-Solid
                      * ``"nw_sw_sd"`` - Pacific Northwest_Swine_Solid
                      * ``"se_bf_lq"`` - Southeast_Beef_Liquid
                      * ``"se_bf_sl"`` - Southeast_Beef_Slurry
                      * ``"se_bf_ss"`` - Southeast_Beef_Semi-Solid
                      * ``"se_bf_sd"`` - Southeast_Beef_Solid
                      * ``"se_da_lq"`` - Southeast_Dairy_Liquid
                      * ``"se_da_sl"`` - Southeast_Dairy_Slurry
                      * ``"se_da_ss"`` - Southeast_Dairy_Semi-Solid
                      * ``"se_da_sd"`` - Southeast_Dairy_Solid
                      * ``"se_po_lq"`` - Southeast_Poultry_Liquid
                      * ``"se_po_sl"`` - Southeast_Poultry_Slurry
                      * ``"se_po_ss"`` - Southeast_Poultry_Semi-Solid
                      * ``"se_po_sd"`` - Southeast_Poultry_Solid
                      * ``"se_sw_lq"`` - Southeast_Swine_Liquid
                      * ``"se_sw_sl"`` - Southeast_Swine_Slurry
                      * ``"se_sw_ss"`` - Southeast_Swine_Semi-Solid
                      * ``"se_sw_sd"`` - Southeast_Swine_Solid
                      * ``"sp_bf_lq"`` - Southern Plains_Beef_Liquid
                      * ``"sp_bf_sl"`` - Southern Plains_Beef_Slurry
                      * ``"sp_bf_ss"`` - Southern Plains_Beef_Semi-Solid
                      * ``"sp_bf_sd"`` - Southern Plains_Beef_Solid
                      * ``"sp_da_lq"`` - Southern Plains_Dairy_Liquid
                      * ``"sp_da_sl"`` - Southern Plains_Dairy_Slurry
                      * ``"sp_da_ss"`` - Southern Plains_Dairy_Semi-Solid
                      * ``"sp_da_sd"`` - Southern Plains_Dairy_Solid
                      * ``"sp_po_lq"`` - Southern Plains_Poultry_Liquid
                      * ``"sp_po_sl"`` - Southern Plains_Poultry_Slurry
                      * ``"sp_po_ss"`` - Southern Plains_Poultry_Semi-Solid
                      * ``"sp_po_sd"`` - Southern Plains_Poultry_Solid
                      * ``"sp_sw_lq"`` - Southern Plains_Swine_Liquid
                      * ``"sp_sw_sl"`` - Southern Plains_Swine_Slurry
                      * ``"sp_sw_ss"`` - Southern Plains_Swine_Semi-Solid
                      * ``"sp_sw_sd"`` - Southern Plains_Swine_Solid
                      * ``"sw_bf_lq"`` - Southwest_Beef_Liquid
                      * ``"sw_bf_sl"`` - Southwest_Beef_Slurry
                      * ``"sw_bf_ss"`` - Southwest_Beef_Semi-Solid
                      * ``"sw_bf_sd"`` - Southwest_Beef_Solid
                      * ``"sw_da_lq"`` - Southwest_Dairy_Liquid
                      * ``"sw_da_sl"`` - Southwest_Dairy_Slurry
                      * ``"sw_da_ss"`` - Southwest_Dairy_Semi-Solid
                      * ``"sw_da_sd"`` - Southwest_Dairy_Solid
                      * ``"sw_po_lq"`` - Southwest_Poultry_Liquid
                      * ``"sw_po_sl"`` - Southwest_Poultry_Slurry
                      * ``"sw_po_ss"`` - Southwest_Poultry_Semi-Solid
                      * ``"sw_po_sd"`` - Southwest_Poultry_Solid
                      * ``"sw_sw_lq"`` - Southwest_Swine_Liquid
                      * ``"sw_sw_sl"`` - Southwest_Swine_Slurry
                      * ``"sw_sw_ss"`` - Southwest_Swine_Semi-Solid
                      * ``"sw_sw_sd"`` - Southwest_Swine_Solid

 ``"fert_amt_ac"``    Amount of fertilizer product applied per acre, the amount is in

                      * gallons/acre if liquid (``*_lq``) or slurry manure (``*_sl``), or
                      * lbs/acre if semi-solid (``*_ss``) or solid manure (``*_sd``)

                      (*required* if ``"fert_name"`` is provided)

Example:              .. code-block::  json

                          {
                            "date": "0000-06-02",
                            "name": "Fert applic. surface broadcast",
                            "type": "op-amendment-fertilizer",
                            "op_id": 22693,
                            "min_n_lbs_ac": 125,
                            "min_p_lbs_ac": 0
                          }

                          // – o r–

                          {
                            "date": "0000-06-02",
                            "name": "Fert applic. surface broadcast",
                            "type": "op-amendment-fertilizer",
                            "op_id": 22693,
                            "fert_name": “sw_da_sl”,
                            "fert_amt_ac": 200
                          }


Notes:                * The service will error out if the provided date values are not
                        in range, are invalid, or specify an invalid period.

====================  ============================================================


...
---



.. [#ftm] For example, The Fieldprint Platform already uses CR_LMOD data as inputs to the WEPP (water) and WEPS (wind) soil erosion model services, and other services supporting various FtM sustainability metrics

