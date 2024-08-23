const fs = require('fs');

console.log("This node.js script is used to convert blocked numbers database from WhatsApp format to the format used by call blocker app.");
console.log("Example usage: node convert-db.js --input=input.json --output=output.json\n");

let inputFilePath = '';
let ouputFilePath = '';

for (const arg of process.argv) {
    if (arg.includes('--input')) {
        inputFilePath = arg.replace('--input=', '');
    } else if (arg.includes('--output')) {
        ouputFilePath = arg.replace('--output=', '');
    }
}

if (!inputFilePath) {
    console.error("Please provide input file path with --input=...");
    process.exit(1);
}
if (!ouputFilePath) {
    console.error("Please provide output file path with --output=...");
    process.exit(1);
}

const inputFile = fs.readFileSync(inputFilePath, { encoding: 'utf8' });
const parsed = JSON.parse(inputFile);
const numbersArray = [];

parsed.forEach(entity => {
    const number = { phone: entity.phone };
    let name = entity.agent_name;
    if (entity.agency_name) {
        name += ` (${entity.agency_name})`;
    }
    number.name = name;
    numbersArray.push(number);
});

fs.writeFileSync(ouputFilePath, JSON.stringify(numbersArray));
