using Newtonsoft.Json;
using System;

namespace ProjectB
{
    public class BClass
    {
        public void ShowData()
        {
            var data = new { Product = "Laptop", Price = 85000 };
            Console.WriteLine(JsonConvert.SerializeObject(data));
        }
    }
}
