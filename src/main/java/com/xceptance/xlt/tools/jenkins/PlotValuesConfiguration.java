package com.xceptance.xlt.tools.jenkins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PlotValuesConfiguration
{
    public static enum CONFIG_VALUE_PARAMETER
    {
     id,
     xPath,
     condition,
     plotID,
     name
    };

    public static enum CONFIG_PLOT_PARAMETER
    {
     id,
     title,
     buildCount,
     enabled,
     showNoValues
    };

    public static enum CONFIG_SECTIONS_PARAMETER
    {
     values,
     plots
    };
    
    private final HashMap<String, Plot> _plots = new HashMap<>();

    private final HashMap<String, Value> _values = new HashMap<>();

    private PlotValuesConfiguration()
    {
        // Empty
    }

    public Plot getPlotById(final String id)
    {
        return _plots.get(id);
    }
    
    public Value getValueById(final String id)
    {
        return _values.get(id);
    }
    
    public Collection<Plot> getPlots()
    {
        return Collections.unmodifiableCollection(_plots.values());
    }
    
    public Collection<Value> getValues()
    {
        return Collections.unmodifiableCollection(_values.values());
    }
    
    public Collection<Plot> getEnabledPlots()
    {
        final ArrayList<Plot> list = new ArrayList<>();
        for(final Plot plot : _plots.values())
        {
            if(plot.enabled)
            {
                list.add(plot);
            }
        }
        
        return list;
    }
    
    public static PlotValuesConfiguration fromJson(final JSONObject json)
    {
        if (json == null)
        {
            return null;
        }

        final PlotValuesConfiguration pvc = new PlotValuesConfiguration();
        final JSONArray plots = json.getJSONArray(CONFIG_SECTIONS_PARAMETER.plots.name());
        for (int i = 0; i < plots.length(); i++)
        {
            final Plot p = Plot.fromJson(plots.getJSONObject(i));
            if (p != null)
                pvc._plots.put(p.id, p);

        }

        final JSONArray values = json.getJSONArray(CONFIG_SECTIONS_PARAMETER.values.name());
        for (int j = 0; j < values.length(); j++)
        {
            final Value v = Value.fromJson(values.getJSONObject(j));
            if (v != null)
            {
                pvc._values.put(v.id, v);
                final Plot p = pvc._plots.get(v.plotID);
                if (p != null)
                {
                    p.valueRefs.add(v.id);
                }
            }
        }

        return pvc;
    }

    public static class Plot
    {
        private final String id;

        private final String title;

        private final int buildCount;

        private final boolean enabled;

        private final boolean showNoValues;

        private final Set<String> valueRefs = new HashSet<>();

        private Plot(final String aId, final String aTitle, final int aCount, final boolean aEnabled, final boolean aShowNoValues)
        {
            id = aId;
            title = aTitle;
            buildCount = aCount;
            enabled = aEnabled;
            showNoValues = aShowNoValues;
        }

        private static Plot fromJson(final JSONObject json) throws JSONException
        {
            if (json != null)
            {
                final String id = json.getString(CONFIG_PLOT_PARAMETER.id.name());
                final String title = json.getString(CONFIG_PLOT_PARAMETER.title.name());
                final String buildCount = json.getString(CONFIG_PLOT_PARAMETER.buildCount.name());
                final String enabled = json.getString(CONFIG_PLOT_PARAMETER.enabled.name());
                final String showNoValues = json.getString(CONFIG_PLOT_PARAMETER.showNoValues.name());

                int count = -1;
                if (StringUtils.isNotBlank(buildCount))
                {
                    try
                    {
                        final int i = Integer.parseInt(buildCount, 10);
                        if (i > 0)
                        {
                            count = i;
                        }
                    }
                    catch (NumberFormatException nfe)
                    {

                    }
                }

                if (StringUtils.isNoneBlank(id, enabled, showNoValues))
                {
                    return new Plot(id, title, count, "yes".equals(enabled), "yes".equals(showNoValues));
                }
            }

            return null;
        }

        public String getId()
        {
            return id;
        }

        public String getTitle()
        {
            return title;
        }

        public int getBuildCount()
        {
            return buildCount;
        }

        public boolean isEnabled()
        {
            return enabled;
        }

        public boolean isShowNoValues()
        {
            return showNoValues;
        }

        public Set<String> getValueRefs()
        {
            return Collections.unmodifiableSet(valueRefs);
        }

    }

    public static class Value
    {
        private final String id;

        private final String name;

        private final String xpath;

        private final String condition;

        private final String plotID;

        private Value(final String aId, final String aName, final String aXPath, final String aCondition, final String aPlotID)
        {
            id = aId;
            name = aName;
            xpath = aXPath;
            condition = aCondition;
            plotID = aPlotID;
        }

        private static Value fromJson(final JSONObject json) throws JSONException
        {
            /*
             * 
             */
            if (json != null)
            {
                final String id = json.getString(CONFIG_VALUE_PARAMETER.id.name());
                final String name = json.getString(CONFIG_VALUE_PARAMETER.name.name());
                final String xpath = json.getString(CONFIG_VALUE_PARAMETER.xPath.name());
                final String cond = json.getString(CONFIG_VALUE_PARAMETER.condition.name());
                final String plotID = json.getString(CONFIG_VALUE_PARAMETER.plotID.name());

                if (StringUtils.isNoneBlank(id, xpath, plotID))
                {
                    return new Value(id, name, xpath, cond, plotID);
                }
            }

            return null;
        }

        public String getId()
        {
            return id;
        }

        public String getName()
        {
            return name;
        }

        public String getXpath()
        {
            return xpath;
        }

        public String getCondition()
        {
            return condition;
        }

        public String getPlotID()
        {
            return plotID;
        }

    }
}
