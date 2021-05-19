# Slicer4J


<img align="right" src="img/slicer4j_logo.png" alt="drawing" width="250"/>


Slicer4J adapts MANDOLINE to work for JAR files! 
You can list Slicer4J options using:  `python3 slicer4j.py -h`

---

## Slicer4J Mandatory Options: 


<table class="tg">
<thead>
  <tr>
    <th class="tg-73oq">Option<br></th>
    <th class="tg-73oq">Description</th>
  </tr>
</thead>
<tbody>
  <tr>
    <td class="tg-73oq">-h</td>
    <td class="tg-73oq">show help message and exit</td>
  </tr>
  <tr>
    <td class="tg-73oq">-j</td>
    <td class="tg-73oq">Path to jar file</td>
  </tr>
  <tr>
    <td class="tg-73oq">-o</td>
    <td class="tg-73oq">Output folder</td>
  </tr>
  <tr>
    <td class="tg-73oq">-b</td>
    <td class="tg-73oq">line to slice backward from, in the form of FileName:LineNumber </td>
  </tr>
</tbody>
</table>

<br>
<br>

## Slicer4J Optional Options: 



<table class="tg">
<thead>
  <tr>
    <th class="tg-73oq">Option<br></th>
    <th class="tg-73oq">Description</th>
  </tr>
</thead>
<tbody>
  <tr>
    <td class="tg-73oq">-m</td>
    <td class="tg-73oq">Main class to run with arguments, in the form of "FileName Arguments"</td>
  </tr>
  <tr>
    <td class="tg-73oq">-tc</td>
    <td class="tg-73oq">Test class name to run, if this is provided, -tm must also be provided</td>
  </tr>
  <tr>
    <td class="tg-73oq">-tm</td>
    <td class="tg-73oq">Test method to run</td>
  </tr>
  <tr>
    <td class="tg-73oq">-dep</td>
    <td class="tg-73oq">Directory to folder containing JAR dependencies, if any</td>
  </tr>
  <tr>
    <td class="tg-0lax">-mod</td>
    <td class="tg-0lax">Folder containing user-defined method models</td>
  </tr>
  <tr>
    <td class="tg-0lax">-d</td>
    <td class="tg-0lax">Slice with data-flow dependencies only</td>
  </tr>
  <tr>
    <td class="tg-0lax">-c</td>
    <td class="tg-0lax">Slice with control dependencies only</td>
  </tr>
</tbody>
</table>

<br>
<br>

## User-defined method models: 

The following is an example for defining your own  method models. 

For the methods in this class:
```Java
package com.myproject;
class MyClass extends MyOtherClass{
    String field;
    public MyClass put(String val){
        this.field = val;
    }
    public String get(){
        return this.field;
    }
}
```

create an XML file named "com.myproject.MyClass.xml" and place it in a folder containing your method models, this is the folder we pass to Slicer4J using the `-mod` option

For example, here's the model for the above class
```xml
<?xml version="1.0" ?>
<summary fileFormatVersion="101">
<hierarchy superClass="com.myproject.MyOtherClass" />
  <methods>
    <method id="com.myproject.MyClass put(java.lang.String)">
      <flows>
        <flow>
          <from sourceSinkType="Parameter" ParameterIndex="0" />
          <to sourceSinkType="Field" AccessPath="[com.myproject.MyClass: java.lang.String field]"
          	AccessPathTypes="java.lang.String" />
        </flow>
        <flow>
          <from sourceSinkType="Field" />
          <to sourceSinkType="Return" />
        </flow>
      </flows>
    </method>
    <method id="java.lang.String get()">
      <flows>
        <flow>
          <from sourceSinkType="Field" AccessPath="[java.nio.CharBuffer: char[] buffer]"
          	AccessPathTypes="[char[]]" />
           <to sourceSinkType="Return" />
        </flow>
      </flows>
    </method>
</summary>
```

The `id` of each method is the method signature. Each method has flows `from` parameters, the receiver, and their fields, `to` other parameters, the receiver, their fields, and the return.


Each flow is specified with it `sourceSinkType` as `Parameter`, `Field`, or `Return`.
`Parameter` is used for parameters. `Field` is used for the receiver or fields of the receiver. `Return` is for the method return.
For parameters, we also need `ParameterIndex` to specify which parameter (first, second, etc.).
For fields, we specify the signature of the field in `AccessPath` and its type in `AccessPathTypes`.



