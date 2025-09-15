package ru.sber.qa.config.services.pacman;


import ru.sber.qa.services.pacman.parameter.SupParameterFilter;

import java.util.Date;
import java.util.List;


public enum TestSupParameterFilters implements SupParameterFilter {

    TEST_ADD_GET_DELL_STRING_LIST_PARAMETER_FILTER(
            List.of("SBPMMPULL-IB-BH-SCHEDULER_SBOL"),
            "ufs.baseurl.compliance.test_s",
            "TEST_STRING",
            String.class,
            List.of("ManageParameters.ParameterRole.System"),
            true,
            List.of("SBOL")
    ),

    TEST_ADD_GET_DELL_STRING_NO_LIST_PARAMETER_FILTER(
            List.of("SBPMMPULL-IB-BH-SCHEDULER_SBOL"),
            "ufs.baseurl.compliance.test_s",
            "TEST_STRING",
            String.class,
            List.of("ManageParameters.ParameterRole.System"),
            false,
            List.of("SBOL")
    ),

    TEST_ADD_GET_LONG_NO_LIST_PARAMETER_FILTER(
            List.of("SBPMMPULL-IB-BH-SCHEDULER_SBOL"),
            "ufs.baseurl.compliance.test_l",
            "TEST_LONG",
            Long.class,
            List.of("ManageParameters.ParameterRole.System"),
            false,
            List.of("SBOL")
    ),

    TEST_ADD_GET_DOUBLE_NO_LIST_PARAMETER_FILTER(
            List.of("SBPMMPULL-IB-BH-SCHEDULER_SBOL"),
            "ufs.baseurl.compliance.test_d",
            "TEST_DOUBLE",
            Double.class,
            List.of("ManageParameters.ParameterRole.System"),
            false,
            List.of("SBOL")
    ),

    TEST_ADD_GET_BOOLEAN_LIST_PARAMETER_FILTER(
            List.of("SBPMMPULL-IB-BH-SCHEDULER_SBOL"),
            "ufs.baseurl.compliance.test_b",
            "TEST_BOOLEAN",
            Boolean.class,
            List.of("ManageParameters.ParameterRole.System"),
            false,
            List.of("SBOL")
    ),

    TEST_ADD_GET_DELL_DATE_LIST_PARAMETER_FILTER(
            List.of("SBPMMPULL-IB-BH-SCHEDULER_SBOL"),
            "ufs.baseurl.compliance.test_date",
            "TEST_DATE",
            Date.class,
            List.of("ManageParameters.ParameterRole.System"),
            true,
            List.of("SBOL")
    ),

    TEST_GET_NULL_PARAMETER_FILTER(
            List.of("SBPMMPULL-IB-BH-SCHEDULER_SBOL"),
            "ufs.baseurl.compliance",
            null,
            String.class,
            null,
            false,
            null
    ),

    TEST_GET_NULL_PARAMETER_FILTER_SEVERAL_VALUES(
            List.of(),
            "ufs.baseurl.compliance",
            null,
            String.class,
            null,
            false,
            null
    );

    private final List<String> paramScopes;
    private final String paramName;
    private final String paramDescription;
    private final Class<?> paramType;
    private final List<String> paramRoles;
    private final Boolean paramIsList;
    private final List<String> paramTenantCodes;

    TestSupParameterFilters(List<String> paramScopes,
                            String paramName,
                            String paramDescription,
                            Class<?> paramType,
                            List<String> paramRoles,
                            Boolean paramIsList,
                            List<String> paramTenantCodes) {
        this.paramScopes = paramScopes;
        this.paramName = paramName;
        this.paramDescription = paramDescription;
        this.paramType = paramType;
        this.paramRoles = paramRoles;
        this.paramIsList = paramIsList;
        this.paramTenantCodes = paramTenantCodes;
    }

    @Override
    public List<String> paramScopes() {
        return this.paramScopes;
    }

    @Override
    public String paramName() {
        return this.paramName;
    }

    @Override
    public String paramDescription() {
        return this.paramDescription;
    }

    @Override
    public Class<?> paramType() {
        return this.paramType;
    }

    @Override
    public List<String> paramRoles() {
        return this.paramRoles;
    }

    @Override
    public Boolean paramIsList() {
        return this.paramIsList;
    }

    @Override
    public List<String> paramTenantCodes() {
        return this.paramTenantCodes;
    }

}
