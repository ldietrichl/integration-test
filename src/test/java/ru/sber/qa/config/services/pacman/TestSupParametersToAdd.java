package ru.sber.qa.config.services.pacman;

import ru.sber.qa.services.pacman.parameter.dto.AddParameterRequestDto;

import java.util.List;

public class TestSupParametersToAdd {

    public static final AddParameterRequestDto TEST_ADD_GET_DELL_STRING_LIST_PARAMETER = new AddParameterRequestDto(
            null,
            "String",
            "ufs.baseurl.compliance.test_s",
            "TEST_STRING",
            "SBPMMPULL-IB-BH-SCHEDULER_SBOL",
            "SBOL",
            true,
            List.of("ManageParameters.ParameterRole.System")
    );

    public static final AddParameterRequestDto TEST_ADD_GET_DELL_STRING_NO_LIST_PARAMETER = new AddParameterRequestDto(
            null,
            "String",
            "ufs.baseurl.compliance.test_s",
            "TEST_STRING",
            "SBPMMPULL-IB-BH-SCHEDULER_SBOL",
            "SBOL",
            false,
            List.of("ManageParameters.ParameterRole.System")
    );

    public static final AddParameterRequestDto TEST_ADD_GET_LONG_NO_LIST_PARAMETER = new AddParameterRequestDto(
            null,
            "Long",
            "ufs.baseurl.compliance.test_l",
            "TEST_LONG",
            "SBPMMPULL-IB-BH-SCHEDULER_SBOL",
            "SBOL",
            false,
            List.of("ManageParameters.ParameterRole.System")
    );

    public static final AddParameterRequestDto TEST_ADD_GET_DOUBLE_NO_LIST_PARAMETER = new AddParameterRequestDto(
            null,
            "Double",
            "ufs.baseurl.compliance.test_d",
            "TEST_DOUBLE",
            "SBPMMPULL-IB-BH-SCHEDULER_SBOL",
            "SBOL",
            false,
            List.of("ManageParameters.ParameterRole.System")
    );

    public static final AddParameterRequestDto TEST_ADD_GET_BOOLEAN_LIST_PARAMETER = new AddParameterRequestDto(
            null,
            "Boolean",
            "ufs.baseurl.compliance.test_b",
            "TEST_BOOLEAN",
            "SBPMMPULL-IB-BH-SCHEDULER_SBOL",
            "SBOL",
            false,
            List.of("ManageParameters.ParameterRole.System")
    );

    public static final AddParameterRequestDto TEST_ADD_GET_DELL_DATE_LIST_PARAMETER = new AddParameterRequestDto(
            null,
            "Date",
            "ufs.baseurl.compliance.test_date",
            "TEST_DATE",
            "SBPMMPULL-IB-BH-SCHEDULER_SBOL",
            "SBOL",
            true,
            List.of("ManageParameters.ParameterRole.System")
    );
}