package com.michalklempa.avro.compose.example;

import com.michalklempa.avro.schemas.common.Department;
import com.michalklempa.avro.schemas.common.GPS;
import com.michalklempa.avro.schemas.common.Location;
import com.michalklempa.avro.schemas.common.PersonalInformation;
import com.michalklempa.avro.schemas.keys.EmployeeKey;
import com.michalklempa.avro.schemas.values.EmployeeValue;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("This is example Avro Class from generated sources: ");
        System.out.println(
                EmployeeValue.newBuilder()
                        .setKeyBuilder(
                                EmployeeKey.newBuilder()
                                        .setDepartment(Department.BACKEND)
                                        .setId(42L)
                        )
                        .setPersonalInformationBuilder(
                                PersonalInformation.newBuilder()
                                        .setName("Michal")
                                        .setSurname("Klempa")
                                        .setLocationBuilder(
                                                Location.newBuilder()
                                                        .setLocationSpec(
                                                                GPS.newBuilder()
                                                                        .setLat(48.153827)
                                                                        .setLon(17.099712)
                                                                        .build()
                                                        )
                                        )

                        ).build().toString()
        );
    }
}
